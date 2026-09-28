package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalysisResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.RuleBasedImpactAnalyzer;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private PublicationAnalysisInput criarInput(
            String title,
            String description
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
                "https://example.com/nota-tecnica.pdf"
        );
    }
}
