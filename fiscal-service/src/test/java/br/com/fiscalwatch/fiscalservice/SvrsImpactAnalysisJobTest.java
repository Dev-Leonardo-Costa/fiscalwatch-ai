package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;
import br.com.fiscalwatch.fiscalservice.impactanalysis.job.*;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.ImpactAnalysisService;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SvrsImpactAnalysisJobTest {
    private final PublicationDocumentRepository documents = mock(PublicationDocumentRepository.class);
    private final ImpactAnalysisService analyses = mock(ImpactAnalysisService.class);
    private final SvrsImpactAnalysisJobProperties properties =
            new SvrsImpactAnalysisJobProperties(true, 300000, 300000, 2);

    @Test
    void configuracaoPadraoNaoDeveCriarJobOuHabilitarAgendamento() {
        new ApplicationContextRunner().withUserConfiguration(SvrsImpactAnalysisJobConfiguration.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean("svrsImpactAnalysisJob"));
                    assertFalse(context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
                });
    }

    @Test
    void deveHabilitarJobComIntervaloELoteConfigurados() {
        new ApplicationContextRunner().withUserConfiguration(SvrsImpactAnalysisJobConfiguration.class)
                .withBean(PublicationDocumentRepository.class, () -> documents)
                .withBean(ImpactAnalysisService.class, () -> analyses)
                .withPropertyValues("fiscalwatch.impact-analysis-job.enabled=true",
                        "fiscalwatch.impact-analysis-job.fixed-delay-ms=60000",
                        "fiscalwatch.impact-analysis-job.initial-delay-ms=3600000",
                        "fiscalwatch.impact-analysis-job.batch-size=7")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var configured = context.getBean(SvrsImpactAnalysisJobProperties.class);
                    assertEquals(60000, configured.fixedDelayMs());
                    assertEquals(7, configured.batchSize());
                    assertNotNull(context.getBean(SvrsImpactAnalysisJob.class));
                    verifyNoInteractions(documents, analyses);
                });
    }

    @Test
    void jobDesativadoNaoDeveConsultarOuAnalisar() {
        new SvrsImpactAnalysisJob(documents, analyses,
                new SvrsImpactAnalysisJobProperties(false, 300000, 300000, 20)).runOnce();
        verifyNoInteractions(documents, analyses);
    }

    @Test
    void configuracaoInvalidaDeveSerRejeitada() {
        assertThrows(IllegalArgumentException.class,
                () -> new SvrsImpactAnalysisJobProperties(true, 0, 0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new SvrsImpactAnalysisJobProperties(true, 1000, -1, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new SvrsImpactAnalysisJobProperties(true, 1000, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SvrsImpactAnalysisJobProperties(true, 1000, 0, 1001));
    }

    @Test
    void falhaEmUmaPublicacaoNaoDeveImpedirDemaisItensNemRepetirNoMesmoCiclo() {
        when(documents.findPendingSvrsAnalysisDocuments(anyLong(), any(), anyString(), any(), any()))
                .thenReturn(List.of(documento(1L), documento(2L)));
        when(analyses.analyzePublication(1L)).thenThrow(new IllegalStateException("Não registrar segredo"));
        when(analyses.analyzePublication(2L)).thenReturn(mock(ImpactAnalysisResponse.class));
        new SvrsImpactAnalysisJob(documents, analyses, properties).runOnce();
        verify(analyses, times(1)).analyzePublication(1L);
        verify(analyses, times(1)).analyzePublication(2L);
        verify(documents).findPendingSvrsAnalysisDocuments(eq(0L), eq(ExtractionStatus.EXTRACTED),
                eq("automatic-v1"), eq(AnalysisStatus.COMPLETED),
                argThat(page -> page.getPageSize() == 2 && page.getPageNumber() == 0));
    }

    @Test
    void documentoComHashInconsistenteNaoDeveSerEnviadoAoAnalisador() {
        var invalid = documento(1L);
        invalid.setContentHash("a".repeat(64));
        when(documents.findPendingSvrsAnalysisDocuments(anyLong(), any(), anyString(), any(), any()))
                .thenReturn(List.of(invalid));
        new SvrsImpactAnalysisJob(documents, analyses, properties).runOnce();
        verifyNoInteractions(analyses);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsDeFalhaNaoDevemExporMensagemOuConteudoSensivel(CapturedOutput output) {
        when(documents.findPendingSvrsAnalysisDocuments(anyLong(), any(), anyString(), any(), any()))
                .thenReturn(List.of(documento(1L)));
        when(analyses.analyzePublication(1L))
                .thenThrow(new IllegalStateException("password=SEGREDO_TESTE; texto fiscal confidencial"));
        new SvrsImpactAnalysisJob(documents, analyses, properties).runOnce();
        assertFalse(output.getAll().contains("SEGREDO_TESTE"));
        assertFalse(output.getAll().contains("texto fiscal confidencial"));
        assertTrue(output.getAll().contains("tipoErro=IllegalStateException"));
    }

    @Test
    void consultaQueFalhaDeveLiberarGuardParaExecucaoPosterior() {
        when(documents.findPendingSvrsAnalysisDocuments(anyLong(), any(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("Falha de conexão"))
                .thenReturn(List.of());
        var job = new SvrsImpactAnalysisJob(documents, analyses, properties);
        job.runOnce();
        job.runOnce();
        verify(documents, times(2)).findPendingSvrsAnalysisDocuments(anyLong(), any(), anyString(), any(), any());
    }

    @Test
    void cursorDeveAvancarEVoltarAoInicioParaRecuperarFalhas() {
        when(documents.findPendingSvrsAnalysisDocuments(anyLong(), any(), anyString(), any(), any()))
                .thenReturn(List.of(documento(5L)), List.of(), List.of(documento(5L)));
        when(analyses.analyzePublication(5L)).thenThrow(new IllegalStateException("Falha"))
                .thenReturn(mock(ImpactAnalysisResponse.class));
        var job = new SvrsImpactAnalysisJob(documents, analyses, properties);
        job.runOnce();
        job.runOnce();
        job.runOnce();
        verify(analyses, times(2)).analyzePublication(5L);
        verify(documents, times(2)).findPendingSvrsAnalysisDocuments(eq(0L), any(), anyString(), any(), any());
        verify(documents).findPendingSvrsAnalysisDocuments(eq(5L), any(), anyString(), any(), any());
    }

    @Test
    void execucaoSobrepostaDeveSerIgnoradaNoMesmoProcesso() throws Exception {
        when(documents.findPendingSvrsAnalysisDocuments(anyLong(), any(), anyString(), any(), any()))
                .thenReturn(List.of(documento(1L)));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(analyses.analyzePublication(1L)).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout do teste");
            return mock(ImpactAnalysisResponse.class);
        });
        var job = new SvrsImpactAnalysisJob(documents, analyses, properties);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(job::runOnce);
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            job.runOnce();
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            verify(analyses, times(1)).analyzePublication(1L);
            verify(documents, times(1)).findPendingSvrsAnalysisDocuments(anyLong(), any(), anyString(), any(), any());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private PublicationDocumentEntity documento(Long id) {
        var publication = new PublicationEntity();
        publication.setId(id);
        var document = new PublicationDocumentEntity();
        document.setPublication(publication);
        document.setExtractionStatus(ExtractionStatus.EXTRACTED);
        document.setContentText("Documento de teste");
        document.setContentLength(document.getContentText().length());
        document.setContentHash(PublicationSnapshotHasher.calculateContentHash(document.getContentText()));
        return document;
    }
}
