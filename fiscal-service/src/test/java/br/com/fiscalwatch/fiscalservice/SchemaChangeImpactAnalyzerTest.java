package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalysisResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.SchemaChangeAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.SchemaChangeImpactAnalyzer;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.SchemaChangeRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaChangeImpactAnalyzerTest {

    private SchemaChangeImpactAnalyzer analyzer;

    @BeforeEach
    void configurar() {
        analyzer = new SchemaChangeImpactAnalyzer();
    }

    @Test
    void deveGerarImpactosAcoesEEvidenciasParaMudancasSuportadas() {

        SchemaChangeAnalysisInput input = new SchemaChangeAnalysisInput(
                criarPublicacao(),
                "external-1",
                "external-0",
                "2025.002 v1.30",
                "2025.002 v1.20",
                List.of(
                        criarChange("TYPE_CHANGED", "vBC"),
                        criarChange("CARDINALITY_CHANGED", "vCredPresIBSZFM"),
                        criarChange("ELEMENT_ADDED", "dPrevEntrega"),
                        criarChange("ELEMENT_REMOVED", "gIBSCredPres"),
                        criarChange("ENUMERATION_ADDED", "TTpNFCredito"),
                        criarChange("ENUMERATION_REMOVED", "TTpNFDebito")
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.HIGH, result.impactLevel());
        assertTrue(result.summary().contains("external-1"));
        assertTrue(result.summary().contains("6"));
        assertEquals(6, result.technicalImpacts().size());
        assertEquals(6, result.actionItems().size());
        assertEquals(6, result.evidences().size());
        assertEquals(
                "Tipo de campo XSD alterado",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(
                "Schemas fiscais",
                result.technicalImpacts().get(0).affectedArea()
        );
        assertEquals(
                "Desenvolvedor fiscal",
                result.actionItems().get(0).targetProfessionalProfile()
        );
        assertTrue(result.evidences().get(0).excerpt()
                .contains("changeType=TYPE_CHANGED"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("before=TDec1302"));
        assertEquals(
                "DFeTiposBasicos_v1.00.xsd",
                result.evidences().get(0).documentSection()
        );
    }

    @Test
    void deveIgnorarMudancasNaoSuportadas() {

        SchemaChangeAnalysisInput input = new SchemaChangeAnalysisInput(
                criarPublicacao(),
                "external-1",
                "external-0",
                "2025.002 v1.30",
                "2025.002 v1.20",
                List.of(criarChange("PATTERN_CHANGED", "TChNFe"))
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.NONE, result.impactLevel());
        assertTrue(result.technicalImpacts().isEmpty());
        assertTrue(result.actionItems().isEmpty());
        assertTrue(result.evidences().isEmpty());
    }

    private PublicationAnalysisInput criarPublicacao() {

        return new PublicationAnalysisInput(
                10L,
                "external-1",
                "SVRS",
                "Nota Tecnica 2025.002",
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 10, 1, 10, 0),
                null,
                "Schemas SVRS",
                "https://example.com/schema.zip",
                null
        );
    }

    private SchemaChangeRequest criarChange(
            String changeType,
            String symbolName
    ) {

        return new SchemaChangeRequest(
                "DFeTiposBasicos_v1.00.xsd",
                changeType,
                "complexType:TCIBS/element:" + symbolName,
                symbolName,
                "TDec1302",
                "TDec1302RTC"
        );
    }
}
