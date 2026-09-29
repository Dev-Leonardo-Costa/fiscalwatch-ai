package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalysisResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationDocumentAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.RuleBasedImpactAnalyzer;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedImpactAnalyzerTest {

    private RuleBasedImpactAnalyzer analyzer;

    @BeforeEach
    void configurar() {
        analyzer = new RuleBasedImpactAnalyzer();
    }

    @Test
    void deveRetornarAnaliseDeterministicaParaPublicacaoComSchema() {

        PublicationAnalysisInput input = criarInput(
                "Nota Tecnica com schema XML",
                "Publicacao altera schema da NF-e"
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(
                "Analise baseada em regras para a publicacao: "
                        + "Nota Tecnica com schema XML",
                result.summary()
        );
        assertEquals(ImpactLevel.HIGH, result.impactLevel());
        assertNull(result.homologationDeadline());
        assertNull(result.productionDeadline());

        assertEquals(1, result.technicalImpacts().size());
        assertEquals(
                "Revisar impacto tecnico da publicacao",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(
                DocumentType.NOTA_TECNICA.name(),
                result.technicalImpacts().get(0).affectedComponent()
        );
        assertEquals(
                ImpactLevel.HIGH,
                result.technicalImpacts().get(0).impactLevel()
        );

        assertEquals(1, result.actionItems().size());
        assertEquals(
                "Analista fiscal",
                result.actionItems().get(0).targetProfessionalProfile()
        );
        assertEquals(ImpactLevel.HIGH, result.actionItems().get(0).priority());

        assertEquals(1, result.evidences().size());
        assertEquals(
                "Publicacao altera schema da NF-e",
                result.evidences().get(0).excerpt()
        );
        assertEquals(
                "https://example.com/nota-tecnica.pdf",
                result.evidences().get(0).sourceUrl()
        );
    }

    @Test
    void deveUsarTituloComoEvidenciaQuandoDescricaoNaoExistir() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                null
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(
                "Comunicado fiscal",
                result.evidences().get(0).excerpt()
        );
    }

    @Test
    void deveUsarContentTextExtraidoParaDefinirImpacto() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        "O documento oficial altera o layout da NF-e."
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.HIGH, result.impactLevel());
    }

    @Test
    void deveUsarMatchDoMotorDeRegrasParaResultadoEspecifico() {

        PublicationAnalysisInput input = criarInput(
                "Nota Técnica 2026.009",
                null,
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        "A publicação altera regra de validação de CFOP "
                                + "aplicada à NF-e."
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(
                "Revisar regra de validação de CFOP",
                result.technicalImpacts().get(0).title()
        );
        assertEquals("NF-e", result.technicalImpacts().get(0).affectedComponent());
        assertEquals(
                "Revisar validação de CFOP",
                result.actionItems().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt().contains("CFOP"));
    }

    @Test
    void deveGerarEvidenceDocumentalQuandoRegraEstiverNoContentText() {

        String contentText = "A".repeat(120)
                + "layout"
                + "B".repeat(120);
        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        contentText
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        var evidence = result.evidences().get(0);

        assertEquals("Comunicado fiscal", evidence.sourceTitle());
        assertEquals("https://example.com/documento.pdf", evidence.sourceUrl());
        assertNull(evidence.documentSection());
        assertNull(evidence.pageNumber());
        assertTrue(evidence.excerpt().contains("layout"));
        assertTrue(evidence.excerpt().length() < contentText.length());
        assertEquals(
                contentText.substring(
                        evidence.startPosition(),
                        evidence.endPosition()
                ),
                evidence.excerpt()
        );
    }

    @Test
    void deveUsarDownloadUrlQuandoSourceUrlDocumentalForNull() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        null,
                        "O documento oficial altera o layout da NF-e."
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(
                "https://example.com/nota-tecnica.pdf",
                result.evidences().get(0).sourceUrl()
        );
    }

    @Test
    void deveManterComportamentoAnteriorQuandoDocumentForNull() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa"
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(
                "Publicacao informativa",
                result.evidences().get(0).excerpt()
        );
        assertNull(result.evidences().get(0).startPosition());
        assertNull(result.evidences().get(0).endPosition());
    }

    @Test
    void deveIgnorarContentTextQuandoDocumentoEstiverPending() {

        assertDocumentoNaoExtraidoUsaFallback(ExtractionStatus.PENDING);
    }

    @Test
    void deveIgnorarContentTextQuandoDocumentoEstiverFailed() {

        assertDocumentoNaoExtraidoUsaFallback(ExtractionStatus.FAILED);
    }

    @Test
    void deveIgnorarContentTextQuandoDocumentoEstiverEmpty() {

        assertDocumentoNaoExtraidoUsaFallback(ExtractionStatus.EMPTY);
    }

    @Test
    void deveUsarFallbackQuandoDocumentoExtractedTiverContentTextVazio() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        "   "
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(
                "Publicacao informativa",
                result.evidences().get(0).excerpt()
        );
        assertEquals(
                "https://example.com/nota-tecnica.pdf",
                result.evidences().get(0).sourceUrl()
        );
    }

    private void assertDocumentoNaoExtraidoUsaFallback(
            ExtractionStatus extractionStatus
    ) {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        extractionStatus,
                        "https://example.com/documento.pdf",
                        "Texto documental menciona schema e layout."
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(
                "Publicacao informativa",
                result.evidences().get(0).excerpt()
        );
        assertEquals(
                "https://example.com/nota-tecnica.pdf",
                result.evidences().get(0).sourceUrl()
        );
        assertNull(result.evidences().get(0).startPosition());
        assertNull(result.evidences().get(0).endPosition());
    }

    private PublicationAnalysisInput criarInput(
            String title,
            String description
    ) {

        return criarInput(title, description, null);
    }

    private PublicationAnalysisInput criarInput(
            String title,
            String description,
            PublicationDocumentAnalysisInput document
    ) {

        return new PublicationAnalysisInput(
                1L,
                "external-1",
                "SVRS",
                title,
                DocumentType.NOTA_TECNICA,
                LocalDateTime.of(2026, 9, 28, 10, 0),
                null,
                description,
                "https://example.com/nota-tecnica.pdf",
                document
        );
    }

    private PublicationDocumentAnalysisInput criarDocumento(
            ExtractionStatus extractionStatus,
            String sourceUrl,
            String contentText
    ) {

        return new PublicationDocumentAnalysisInput(
                sourceUrl,
                contentText,
                "a".repeat(64),
                contentText == null ? null : contentText.length(),
                extractionStatus,
                extractionStatus == ExtractionStatus.FAILED
                        ? "Falha na extracao"
                        : null,
                "svrs-pypdf-v1",
                LocalDateTime.of(2026, 9, 29, 10, 0)
        );
    }
}
