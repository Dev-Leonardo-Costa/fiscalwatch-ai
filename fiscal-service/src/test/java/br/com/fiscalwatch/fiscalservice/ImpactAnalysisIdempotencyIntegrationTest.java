package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.*;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.job.SvrsImpactAnalysisJob;
import br.com.fiscalwatch.fiscalservice.impactanalysis.job.SvrsImpactAnalysisJobProperties;
import br.com.fiscalwatch.fiscalservice.impactanalysis.controller.ImpactAnalysisController;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.impactanalysis.exception.InvalidPublicationDocumentException;
import br.com.fiscalwatch.fiscalservice.impactanalysis.mapper.ImpactAnalysisMapper;
import br.com.fiscalwatch.fiscalservice.impactanalysis.repository.ImpactAnalysisRepository;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.ImpactAnalysisService;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationDocumentEvent;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEvent;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Transações e locks reais, em H2 exclusivo em memória; nenhum serviço externo. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:impact-idempotency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "fiscalwatch.impact-analysis-job.enabled=true",
        "fiscalwatch.impact-analysis-job.initial-delay-ms=3600000",
        "fiscalwatch.impact-analysis-job.fixed-delay-ms=3600000",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class ImpactAnalysisIdempotencyIntegrationTest {
    @Autowired private ImpactAnalysisService service;
    @Autowired private PublicationService publicationService;
    @Autowired private PublicationRepository publications;
    @Autowired private PublicationDocumentRepository documents;
    @Autowired private ImpactAnalysisRepository analyses;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SvrsImpactAnalysisJob job;
    @MockitoBean private ImpactAnalyzer analyzer;
    @MockitoSpyBean private ImpactAnalysisMapper mapper;

    private PublicationRequest request;
    private Long publicationId;
    private final List<Long> extraPublicationIds = new java.util.ArrayList<>();

    @BeforeEach
    void preparar() {
        // Falha antes de qualquer fixture se a configuração de isolamento regredir.
        assertTrue(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                connection -> connection.getMetaData().getURL().startsWith("jdbc:h2:mem:")));
        request = new PublicationRequest(UUID.randomUUID().toString(), "SVRS",
                "Nota Técnica de teste", DocumentType.NOTA_TECNICA, LocalDateTime.now(),
                null, "Regra de CFOP", "https://example.test/nota.pdf");
        persistir(ExtractionStatus.EXTRACTED);
        when(analyzer.analyze(any())).thenReturn(resultado());
    }

    @AfterEach
    void removerSomenteFixtureEmMemoria() {
        if (publicationId == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Long extraId : extraPublicationIds) {
                analyses.deleteAll(analyses.findByPublicationId(extraId));
                analyses.flush();
                documents.findByPublicationId(extraId).ifPresent(documents::delete);
                documents.flush();
                publications.deleteById(extraId);
                publications.flush();
            }
            analyses.deleteAll(analyses.findByPublicationId(publicationId));
            analyses.flush();
            documents.findByPublicationId(publicationId).ifPresent(documents::delete);
            documents.flush();
            publications.deleteById(publicationId);
        });
    }

    @Test
    void estadoDeColetaDeveIncluirNotaTecnicaSemDocumentoEmBancoEmMemoria() {
        var initial = publicationService.findSvrsCollectionState(request.externalId());
        assertTrue(initial.exists());
        assertTrue(initial.validDocument());
        documents.delete(documents.findByPublicationId(publicationId).orElseThrow());
        documents.flush();
        var missing = publicationService.findSvrsCollectionState(request.externalId());
        assertTrue(missing.exists());
        assertNull(missing.extractionStatus());
        assertFalse(missing.validDocument());
        assertFalse(publicationService.findSvrsCollectionState("0".repeat(64)).exists());
    }

    @ParameterizedTest
    @EnumSource(value = ExtractionStatus.class, names = {"FAILED", "PENDING"})
    void estadoDeColetaDeveRefletirRecuperacaoPersistida(ExtractionStatus status) {
        var document = documents.findByPublicationId(publicationId).orElseThrow();
        document.setExtractionStatus(status);
        documents.saveAndFlush(document);
        var failed = publicationService.findSvrsCollectionState(request.externalId());
        assertEquals(status, failed.extractionStatus());
        assertFalse(failed.validDocument());
        persistir(ExtractionStatus.EXTRACTED);
        assertTrue(publicationService.findSvrsCollectionState(request.externalId()).validDocument());
        assertEquals(1, documents.findAll().stream()
                .filter(d -> d.getPublication().getId().equals(publicationId)).count());
    }

    @Test
    void segundaChamadaDeveRetornarMesmoIdSemDuplicarRelacionamentos() {
        var first = service.analyzePublication(publicationId);
        var second = service.analyzePublication(publicationId);
        assertMesmaRespostaPersistida(first, second);
        assertEquals(1, analyses.findByPublicationId(publicationId).size());
        assertRelacionamentosUnicos();
        verify(analyzer, times(1)).analyze(any());
    }

    @Test
    void criacaoGenericaComVersaoReservadaDeveReutilizarRegraDocumental() {
        var automaticRequest = new ImpactAnalysisRequest(publicationId, "Resumo externo",
                ImpactLevel.HIGH, "automatic-v1", null, null, List.of(), List.of(), List.of());
        var first = service.create(automaticRequest);
        var second = service.analyzePublication(publicationId);
        assertMesmaRespostaPersistida(first, second);
        assertEquals("Análise de teste", first.summary());
        assertEquals(1, analyses.findByPublicationId(publicationId).size());
        assertRelacionamentosUnicos();
        verify(analyzer, times(1)).analyze(any());
    }

    @Test
    void chamadasConcorrentesDevemAguardarLockERetornarMesmaAnalise() throws Exception {
        var enteredAnalyzer = new CountDownLatch(1);
        var releaseAnalyzer = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        when(analyzer.analyze(any())).thenAnswer(invocation -> {
            enteredAnalyzer.countDown();
            if (!releaseAnalyzer.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout do teste de concorrência");
            }
            return resultado();
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.analyzePublication(publicationId));
            assertTrue(enteredAnalyzer.await(10, TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return service.analyzePublication(publicationId);
            });
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            releaseAnalyzer.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS).id(), second.get(15, TimeUnit.SECONDS).id());
            assertEquals(1, analyses.findByPublicationId(publicationId).size());
            assertRelacionamentosUnicos();
            verify(analyzer, times(1)).analyze(any());
        } finally {
            releaseAnalyzer.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test
    void analiseDeSchemaNaoDeveImpedirAnaliseDocumental() {
        var schema = new ImpactAnalysis();
        schema.setPublication(publications.findById(publicationId).orElseThrow());
        schema.setStatus(AnalysisStatus.COMPLETED);
        schema.setImpactLevel(ImpactLevel.HIGH);
        schema.setAnalysisVersion("schema-comparison-v2");
        schema.setComparisonHash("a".repeat(64));
        analyses.saveAndFlush(schema);
        var result = service.analyzePublication(publicationId);
        assertEquals("automatic-v1", result.analysisVersion());
        assertEquals(2, analyses.findByPublicationId(publicationId).size());
        verify(analyzer, times(1)).analyze(any());
    }

    @Test
    void analisePendenteNaoDeveImpedirDocumentalConcluida() {
        var pending = new ImpactAnalysis();
        pending.setPublication(publications.findById(publicationId).orElseThrow());
        pending.setStatus(AnalysisStatus.PENDING);
        pending.setImpactLevel(ImpactLevel.LOW);
        pending.setAnalysisVersion("automatic-v1");
        analyses.saveAndFlush(pending);
        var result = service.analyzePublication(publicationId);
        assertEquals(AnalysisStatus.COMPLETED, result.status());
        assertEquals(result.id(), service.analyzePublication(publicationId).id());
        assertEquals(1, analyses.findByPublicationId(publicationId).stream()
                .filter(a -> a.getStatus() == AnalysisStatus.COMPLETED).count());
    }

    @ParameterizedTest
    @EnumSource(value = ExtractionStatus.class, names = {"FAILED", "PENDING"})
    void recuperacaoDoDocumentoDevePermitirAnalisePosterior(ExtractionStatus status) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var document = documents.findByPublicationId(publicationId).orElseThrow();
            document.setExtractionStatus(status);
            document.setContentText(null);
            document.setContentHash(null);
            document.setContentLength(null);
            document.setExtractionError(status == ExtractionStatus.FAILED ? "Falha de teste" : null);
        });
        Long documentId = documents.findByPublicationId(publicationId).orElseThrow().getId();
        assertThrows(InvalidPublicationDocumentException.class,
                () -> service.analyzePublication(publicationId));
        assertTrue(analyses.findByPublicationId(publicationId).isEmpty());
        verify(analyzer, never()).analyze(any());
        persistir(ExtractionStatus.EXTRACTED);
        assertEquals(documentId, documents.findByPublicationId(publicationId).orElseThrow().getId());
        assertEquals(AnalysisStatus.COMPLETED, service.analyzePublication(publicationId).status());
        assertEquals(1, analyses.findByPublicationId(publicationId).size());
    }

    @Test
    void falhaDoAnalisadorDevePermitirNovaTentativaSemAnaliseParcial() {
        when(analyzer.analyze(any())).thenThrow(new IllegalStateException("Falha de teste"));
        assertThrows(IllegalStateException.class, () -> service.analyzePublication(publicationId));
        assertTrue(analyses.findByPublicationId(publicationId).isEmpty());
        doReturn(resultado()).when(analyzer).analyze(any());
        service.analyzePublication(publicationId);
        assertRelacionamentosUnicos();
    }

    @Test
    void falhaAposSalvarDeveReverterAnaliseEFilhosELiberarLock() {
        doThrow(new IllegalStateException("Falha ao mapear após persistência"))
                .when(mapper).toResponse(any(ImpactAnalysis.class));
        assertThrows(IllegalStateException.class, () -> service.analyzePublication(publicationId));
        assertTrue(analyses.findByPublicationId(publicationId).isEmpty());
        for (String table : List.of("technical_impacts", "action_items", "evidences")) {
            assertEquals(0L, jdbc.queryForObject("select count(*) from " + table, Long.class));
        }
        doCallRealMethod().when(mapper).toResponse(any(ImpactAnalysis.class));
        service.analyzePublication(publicationId);
        assertRelacionamentosUnicos();
    }

    @ParameterizedTest
    @EnumSource(ExtractionStatus.class)
    void consultaDePendenciasDeveSelecionarSomenteSvrsExtraido(ExtractionStatus status) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                documents.findByPublicationId(publicationId).orElseThrow().setExtractionStatus(status));
        var pending = pendentes(20);
        assertEquals(status == ExtractionStatus.EXTRACTED ? 1 : 0, pending.size());
        if (!pending.isEmpty()) assertEquals(publicationId, pending.get(0).getPublication().getId());
    }

    @Test
    void consultaDePendenciasDeveIgnorarOutrasFontesEDocumentoInvalido() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                publications.findById(publicationId).orElseThrow().setSource("OUTRA_FONTE"));
        assertTrue(pendentes(20).isEmpty());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            publications.findById(publicationId).orElseThrow().setSource("SVRS");
            documents.findByPublicationId(publicationId).orElseThrow().setContentHash(null);
        });
        assertTrue(pendentes(20).isEmpty());
    }

    @Test
    void jobDeveIgnorarAnaliseDocumentalConcluidaESerIdempotenteNasReexecucoes() {
        job.runOnce();
        var first = service.findByPublicationId(publicationId).get(0);
        assertTrue(pendentes(20).isEmpty());
        job.runOnce();
        job.runOnce();
        assertEquals(first.id(), service.findByPublicationId(publicationId).get(0).id());
        assertEquals(1, analyses.findByPublicationId(publicationId).size());
        verify(analyzer, times(1)).analyze(any());
    }

    @Test
    void jobDeveCriarDocumentalMesmoComAnaliseDeSchema() {
        var schema = new ImpactAnalysis();
        schema.setPublication(publications.findById(publicationId).orElseThrow());
        schema.setStatus(AnalysisStatus.COMPLETED);
        schema.setImpactLevel(ImpactLevel.HIGH);
        schema.setAnalysisVersion("schema-comparison-v2");
        analyses.saveAndFlush(schema);
        assertEquals(1, pendentes(20).size());
        job.runOnce();
        assertEquals(2, analyses.findByPublicationId(publicationId).size());
        assertTrue(pendentes(20).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = ExtractionStatus.class, names = {"FAILED", "PENDING"})
    void documentoRecuperadoDeveFicarElegivelParaJob(ExtractionStatus status) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                documents.findByPublicationId(publicationId).orElseThrow().setExtractionStatus(status));
        assertTrue(pendentes(20).isEmpty());
        persistir(ExtractionStatus.EXTRACTED);
        assertEquals(1, pendentes(20).size());
        job.runOnce();
        assertEquals(1, analyses.findByPublicationId(publicationId).size());
    }

    @Test
    void reinicioDoJobDeveRecuperarPendenciaAposFalhaSemControlePersistido() {
        when(analyzer.analyze(any())).thenThrow(new IllegalStateException("Falha de teste"));
        job.runOnce();
        assertTrue(analyses.findByPublicationId(publicationId).isEmpty());
        doReturn(resultado()).when(analyzer).analyze(any());
        new SvrsImpactAnalysisJob(documents, service,
                new SvrsImpactAnalysisJobProperties(true, 300000, 300000, 20)).runOnce();
        assertEquals(1, analyses.findByPublicationId(publicationId).size());
    }

    @Test
    void jobEPostConcorrentesDevemCompartilharLockEAnalise() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var postStarted = new CountDownLatch(1);
        when(analyzer.analyze(any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout do teste");
            return resultado();
        });
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new ImpactAnalysisController(service)).build();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var scheduled = executor.submit(job::runOnce);
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            var manual = executor.submit(() -> {
                postStarted.countDown();
                return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/impact-analyses/publications/{id}/analyze", publicationId))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                        .andReturn();
            });
            assertTrue(postStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> manual.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            scheduled.get(15, TimeUnit.SECONDS);
            var result = manual.get(15, TimeUnit.SECONDS);
            var existing = service.findByPublicationId(publicationId).get(0);
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id")
                    .value(existing.id()).match(result);
            assertEquals(1, analyses.findByPublicationId(publicationId).size());
            assertRelacionamentosUnicos();
            verify(analyzer, times(1)).analyze(any());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test
    void falhaNoPrimeiroItemDeveReverterSomenteSuaTransacaoEConfirmarSegundo() {
        Long secondId = criarPublicacaoExtra();
        when(analyzer.analyze(any())).thenAnswer(invocation -> {
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive());
            PublicationAnalysisInput input = invocation.getArgument(0);
            if (input.id().equals(publicationId)) throw new IllegalStateException("Falha do primeiro item");
            return resultado();
        });
        job.runOnce();
        assertTrue(analyses.findByPublicationId(publicationId).isEmpty());
        assertEquals(1, analyses.findByPublicationId(secondId).size());
        assertEquals(AnalysisStatus.COMPLETED, service.findByPublicationId(secondId).get(0).status());
    }

    @Test
    void consultaDeveRespeitarLimiteEOrdenacaoPorIdComCursor() {
        Long secondId = criarPublicacaoExtra();
        var firstBatch = pendentes(1);
        assertEquals(1, firstBatch.size());
        assertEquals(publicationId, firstBatch.get(0).getPublication().getId());
        var next = documents.findPendingSvrsAnalysisDocuments(publicationId, ExtractionStatus.EXTRACTED,
                "automatic-v1", AnalysisStatus.COMPLETED, org.springframework.data.domain.PageRequest.of(0, 1));
        assertEquals(1, next.size());
        assertEquals(secondId, next.get(0).getPublication().getId());
    }

    private Long criarPublicacaoExtra() {
        var extra = new PublicationRequest(UUID.randomUUID().toString(), "SVRS", "Segunda NT",
                DocumentType.NOTA_TECNICA, LocalDateTime.now(), null, "CFOP", request.downloadUrl());
        String text = "Segundo documento válido";
        publicationService.processEvent(new PublicationEvent("publication.discovered", LocalDateTime.now(), extra,
                new PublicationDocumentEvent(extra.downloadUrl(), text,
                        PublicationSnapshotHasher.calculateContentHash(text), text.length(), ExtractionStatus.EXTRACTED,
                        null, "svrs-pypdf-v1", LocalDateTime.now())));
        Long id = publications.findByExternalId(extra.externalId()).orElseThrow().getId();
        extraPublicationIds.add(id);
        return id;
    }

    private List<br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity> pendentes(int size) {
        return documents.findPendingSvrsAnalysisDocuments(0, ExtractionStatus.EXTRACTED,
                "automatic-v1", AnalysisStatus.COMPLETED, org.springframework.data.domain.PageRequest.of(0, size));
    }

    private void assertMesmaRespostaPersistida(ImpactAnalysisResponse first,
                                              ImpactAnalysisResponse second) {
        // TIMESTAMP usa microssegundos; LocalDateTime.now() pode conter nanossegundos.
        assertTrue(Math.abs(java.time.Duration.between(first.analyzedAt(), second.analyzedAt())
                .toNanos()) < 1000);
        assertEquals(new ImpactAnalysisResponse(first.id(), first.publicationId(), first.summary(),
                first.impactLevel(), first.status(), first.analysisVersion(), first.homologationDeadline(),
                first.productionDeadline(), second.analyzedAt(), first.technicalImpacts(), first.actionItems(),
                first.evidences(), first.createdAt(), first.updatedAt()), second);
    }

    private void assertRelacionamentosUnicos() {
        var result = service.findByPublicationId(publicationId).stream()
                .filter(a -> a.analysisVersion().equals("automatic-v1")
                        && a.status() == AnalysisStatus.COMPLETED).findFirst().orElseThrow();
        assertEquals(1, result.technicalImpacts().size());
        assertEquals(1, result.actionItems().size());
        assertEquals(1, result.evidences().size());
    }

    private void persistir(ExtractionStatus status) {
        String text = "Texto válido com regra I08-140 e CFOP 1.949.";
        var document = new PublicationDocumentEvent(request.downloadUrl(), text,
                PublicationSnapshotHasher.calculateContentHash(text), text.codePointCount(0, text.length()),
                status, null, "svrs-pypdf-v1", LocalDateTime.now());
        publicationService.processEvent(new PublicationEvent("publication.discovered",
                LocalDateTime.now(), request, document));
        publicationId = publications.findByExternalId(request.externalId()).orElseThrow().getId();
    }

    private ImpactAnalysisResult resultado() {
        return new ImpactAnalysisResult("Análise de teste", ImpactLevel.MEDIUM, null, null,
                List.of(new TechnicalImpactResult("CFOP", "Revisar", "Fiscal", "NF-e", ImpactLevel.MEDIUM)),
                List.of(new ActionItemResult("Revisar", "CFOP", "ERP", ImpactLevel.MEDIUM, null)),
                List.of(new EvidenceResult("NT", request.downloadUrl(), null, null, "CFOP", 34, 38)));
    }
}
