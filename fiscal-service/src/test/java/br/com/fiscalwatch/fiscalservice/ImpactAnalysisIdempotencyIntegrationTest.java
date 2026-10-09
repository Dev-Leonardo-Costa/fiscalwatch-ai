package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.*;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
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
    @MockitoBean private ImpactAnalyzer analyzer;
    @MockitoSpyBean private ImpactAnalysisMapper mapper;

    private PublicationRequest request;
    private Long publicationId;

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
            analyses.deleteAll(analyses.findByPublicationId(publicationId));
            analyses.flush();
            documents.findByPublicationId(publicationId).ifPresent(documents::delete);
            documents.flush();
            publications.deleteById(publicationId);
        });
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
