package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalysisResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.SchemaChangeAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.SchemaChangeImpactAnalyzer;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.SchemaChangeRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.XsdTypeDefinitionRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
                "Tipo XSD alterado sem definição completa disponível",
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

    @Test
    void deveClassificarTypeChangedComDefinicaoAnteriorAusente() {

        ImpactAnalysisResult result = analisarTypeChanged(
                null,
                tipo("TDec1302RTC", "xs:string")
        );

        assertEquals(
                "Tipo XSD alterado sem definição completa disponível",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.MEDIUM, result.technicalImpacts().get(0)
                .impactLevel());
        assertEquals(
                "Revisar manualmente os tipos TDec1302 e TDec1302RTC",
                result.actionItems().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt()
                .contains("classification=MISSING_TYPE_DEFINITION"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("beforeDefinition=<ausente>"));
    }

    @Test
    void deveClassificarTypeChangedComDefinicaoPosteriorAusente() {

        ImpactAnalysisResult result = analisarTypeChanged(
                tipo("TDec1302", "xs:string"),
                null
        );

        assertEquals(
                "Tipo XSD alterado sem definição completa disponível",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.MEDIUM, result.actionItems().get(0)
                .priority());
        assertTrue(result.evidences().get(0).excerpt()
                .contains("afterDefinition=<ausente>"));
    }

    @Test
    void deveClassificarTypeChangedComAmbasDefinicoesAusentes() {

        ImpactAnalysisResult result = analisarTypeChanged(null, null);

        assertEquals(
                "Tipo XSD alterado sem definição completa disponível",
                result.technicalImpacts().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt()
                .contains("beforeDefinition=<ausente>"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("afterDefinition=<ausente>"));
    }

    @Test
    void deveClassificarTypeChangedComDefinicoesEquivalentes() {

        ImpactAnalysisResult result = analisarTypeChanged(
                tipoCompleto(
                        "TDec1302",
                        "xs:string",
                        List.of("0|0\\.[0-9]{2}"),
                        List.of(),
                        Map.of("whiteSpace", "preserve")
                ),
                tipoCompleto(
                        "TDec1302RTC",
                        "xs:string",
                        List.of("0|0\\.[0-9]{2}"),
                        List.of(),
                        Map.of("whiteSpace", "preserve")
                )
        );

        assertEquals(
                "Tipo XSD referenciado alterado sem mudança aparente de restrição",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.MEDIUM, result.technicalImpacts().get(0)
                .impactLevel());
        assertEquals(
                "Verificar compatibilidade do novo tipo TDec1302RTC",
                result.actionItems().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt()
                .contains("classification=NOMINAL_ONLY_EQUIVALENT_DEFINITION"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("definições efetivas equivalentes"));
    }

    @Test
    void deveClassificarTypeChangedComBaseAlterada() {

        ImpactAnalysisResult result = analisarTypeChanged(
                tipo("TValorAntigo", "xs:string"),
                tipo("TValorNovo", "xs:decimal")
        );

        assertEquals(
                "Base do tipo XSD alterada",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.HIGH, result.technicalImpacts().get(0)
                .impactLevel());
        assertEquals(
                "Adequar modelo e serialização para nova base XSD",
                result.actionItems().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt()
                .contains("classification=BASE_CHANGED"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("base: xs:string -> xs:decimal"));
    }

    @Test
    void deveClassificarTypeChangedComPatternAlterado() {

        ImpactAnalysisResult result = analisarTypeChanged(
                tipoComPattern("TValorAntigo", "[0-9]{2}"),
                tipoComPattern("TValorNovo", "[0-9]{4}")
        );

        assertEquals(
                "Formato aceito pelo tipo XSD alterado",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.HIGH, result.actionItems().get(0)
                .priority());
        assertEquals(
                "Atualizar validações de formato de vBC",
                result.actionItems().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt()
                .contains("classification=PATTERN_CHANGED"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("patterns: [[0-9]{2}] -> [[0-9]{4}]"));
    }

    @Test
    void deveClassificarTypeChangedComFacetsAlteradasDeAltoImpacto() {

        ImpactAnalysisResult result = analisarTypeChanged(
                tipoComFacets("TValorAntigo", Map.of("fractionDigits", "2")),
                tipoComFacets("TValorNovo", Map.of("fractionDigits", "4"))
        );

        assertEquals(
                "Restrições do tipo XSD alteradas",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.HIGH, result.technicalImpacts().get(0)
                .impactLevel());
        assertEquals(
                "Atualizar restrições de validação de vBC",
                result.actionItems().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt()
                .contains("classification=FACETS_CHANGED"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("fractionDigits: 2 -> 4"));
    }

    @Test
    void deveClassificarTypeChangedComFacetsAlteradasDeMedioImpacto() {

        ImpactAnalysisResult result = analisarTypeChanged(
                tipoComFacets("TValorAntigo", Map.of("whiteSpace", "preserve")),
                tipoComFacets("TValorNovo", Map.of("whiteSpace", "collapse"))
        );

        assertEquals(
                "Restrições do tipo XSD alteradas",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.MEDIUM, result.technicalImpacts().get(0)
                .impactLevel());
        assertTrue(result.evidences().get(0).excerpt()
                .contains("whiteSpace: preserve -> collapse"));
    }

    @Test
    void deveClassificarTypeChangedComEnumeracaoAdicionada() {

        ImpactAnalysisResult result = analisarTypeChanged(
                tipoComEnumeracoes("TDominioAntigo", List.of("01")),
                tipoComEnumeracoes("TDominioNovo", List.of("01", "02"))
        );

        assertEquals(
                "Domínio permitido pelo tipo XSD alterado",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.MEDIUM, result.technicalImpacts().get(0)
                .impactLevel());
        assertEquals(
                "Atualizar domínio permitido de vBC",
                result.actionItems().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt()
                .contains("classification=ENUMERATIONS_CHANGED"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("adicionadas=[02], removidas=[]"));
    }

    @Test
    void deveClassificarTypeChangedComEnumeracaoRemovida() {

        ImpactAnalysisResult result = analisarTypeChanged(
                tipoComEnumeracoes("TDominioAntigo", List.of("01", "02")),
                tipoComEnumeracoes("TDominioNovo", List.of("01"))
        );

        assertEquals(
                "Domínio permitido pelo tipo XSD alterado",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.HIGH, result.technicalImpacts().get(0)
                .impactLevel());
        assertTrue(result.evidences().get(0).excerpt()
                .contains("adicionadas=[], removidas=[02]"));
    }

    @Test
    void deveClassificarTypeChangedComMultiplasDimensoesAlteradas() {

        ImpactAnalysisResult result = analisarTypeChanged(
                tipoCompleto(
                        "TValorAntigo",
                        "xs:string",
                        List.of("[0-9]{2}"),
                        List.of(),
                        Map.of("fractionDigits", "2")
                ),
                tipoCompleto(
                        "TValorNovo",
                        "xs:decimal",
                        List.of("[0-9]{4}"),
                        List.of(),
                        Map.of("fractionDigits", "4")
                )
        );

        assertEquals(
                "Definição do tipo XSD alterada em múltiplas restrições",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(ImpactLevel.HIGH, result.technicalImpacts().get(0)
                .impactLevel());
        assertEquals(
                "Revisar definição completa do tipo TDec1302RTC",
                result.actionItems().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt()
                .contains("classification=MIXED_DEFINITION_CHANGED"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("base: xs:string -> xs:decimal"));
        assertTrue(result.evidences().get(0).excerpt()
                .contains("fractionDigits: 2 -> 4"));
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
                "TDec1302RTC",
                null,
                null
        );
    }

    private ImpactAnalysisResult analisarTypeChanged(
            XsdTypeDefinitionRequest beforeDefinition,
            XsdTypeDefinitionRequest afterDefinition
    ) {

        return analyzer.analyze(new SchemaChangeAnalysisInput(
                criarPublicacao(),
                "external-1",
                "external-0",
                "2025.002 v1.30",
                "2025.002 v1.20",
                List.of(new SchemaChangeRequest(
                        "DFeTiposBasicos_v1.00.xsd",
                        "TYPE_CHANGED",
                        "complexType:TCIBS/element:vBC",
                        "vBC",
                        "TDec1302",
                        "TDec1302RTC",
                        beforeDefinition,
                        afterDefinition
                ))
        ));
    }

    private XsdTypeDefinitionRequest tipo(String name, String base) {

        return tipoCompleto(
                name,
                base,
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private XsdTypeDefinitionRequest tipoComPattern(
            String name,
            String pattern
    ) {

        return tipoCompleto(
                name,
                "xs:string",
                List.of(pattern),
                List.of(),
                Map.of()
        );
    }

    private XsdTypeDefinitionRequest tipoComFacets(
            String name,
            Map<String, String> facets
    ) {

        return tipoCompleto(
                name,
                "xs:string",
                List.of(),
                List.of(),
                facets
        );
    }

    private XsdTypeDefinitionRequest tipoComEnumeracoes(
            String name,
            List<String> enumerations
    ) {

        return tipoCompleto(
                name,
                "xs:string",
                List.of(),
                enumerations,
                Map.of()
        );
    }

    private XsdTypeDefinitionRequest tipoCompleto(
            String name,
            String base,
            List<String> patterns,
            List<String> enumerations,
            Map<String, String> facets
    ) {

        return new XsdTypeDefinitionRequest(
                name,
                "DFeTiposBasicos_v1.00.xsd",
                "simpleType:" + name + "/restriction:" + base,
                base,
                patterns,
                enumerations,
                facets
        );
    }
}
