package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChange;
import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChangeType;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.CfopValidationRule;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.DocumentContext;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.FiscalAnalysisContext;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.RuleMatch;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfopValidationRuleTest {

    private final CfopValidationRule rule = new CfopValidationRule();

    @Test
    void deveGerarMatchParaCfopComRegraDeValidacao() {
        String contentText = "A publicação altera regra de validação de "
                + "CFOP aplicada à NF-e.";

        Optional<RuleMatch> result = rule.evaluate(contextoComDocumento(
                contentText
        ));

        assertTrue(result.isPresent());

        RuleMatch match = result.orElseThrow();

        assertEquals("cfop-validation", match.ruleId());
        assertEquals(1, match.technicalImpacts().size());
        assertEquals(
                "Revisar regra de validação de CFOP",
                match.technicalImpacts().get(0).title()
        );
        assertEquals(
                "A publicação oficial indica alteração relacionada "
                        + "à regra de validação de CFOP.",
                match.technicalImpacts().get(0).description()
        );
        assertEquals("NF-e", match.technicalImpacts().get(0).affectedComponent());

        assertEquals(2, match.actionItems().size());
        assertEquals(
                "Revisar validação de CFOP",
                match.actionItems().get(0).title()
        );
        assertEquals(
                "Verificar regras fiscais correspondentes no ERP",
                match.actionItems().get(1).title()
        );
    }

    @Test
    void deveReconhecerNfeProximaAoContextoDeCfop() {
        RuleMatch match = rule.evaluate(contextoComDocumento(
                "Alteração de Regra de Validação de\n"
                        + "CFOP\n\n"
                        + "Nota Técnica 2026.009 - Versão 1.00\n"
                        + "Setembro de 2026\n\n"
                        + "Nota Fiscal Eletrônica - NF-e\n"
                        + "Alteração de Regra de Validação de CFOP"
        )).orElseThrow();

        assertEquals("NF-e", match.technicalImpacts().get(0).affectedComponent());
    }

    @Test
    void deveReconhecerNotaFiscalEletronicaProximaAoContextoDeCfop() {
        RuleMatch match = rule.evaluate(contextoComDocumento(
                "Alteração de Regra de Validação de CFOP "
                        + "para Nota Fiscal Eletronica modelo 55."
        )).orElseThrow();

        assertEquals("NF-e", match.technicalImpacts().get(0).affectedComponent());
    }

    @Test
    void naoDeveAssociarNfeQuandoEstiverMuitoDistante() {
        RuleMatch match = rule.evaluate(contextoComDocumento(
                "Alteração de regra de validação de CFOP."
                        + "X".repeat(260)
                        + "Nota Fiscal Eletrônica - NF-e"
        )).orElseThrow();

        assertEquals(
                "Documento fiscal",
                match.technicalImpacts().get(0).affectedComponent()
        );
    }

    @Test
    void naoDeveGerarMatchSomenteComCfop() {
        Optional<RuleMatch> result = rule.evaluate(contextoComDocumento(
                "A publicação menciona CFOP em texto informativo."
        ));

        assertFalse(result.isPresent());
    }

    @Test
    void naoDeveGerarMatchSomenteComValidacao() {
        Optional<RuleMatch> result = rule.evaluate(contextoComDocumento(
                "A publicação menciona validação cadastral."
        ));

        assertFalse(result.isPresent());
    }

    @Test
    void deveGerarEvidenceRealCurtaComOffsetsExatos() {
        String contentText = "A".repeat(120)
                + "regra de validação de CFOP"
                + " Nota Fiscal Eletrônica"
                + "B".repeat(120);

        RuleMatch match = rule.evaluate(contextoComDocumento(contentText))
                .orElseThrow();

        var evidence = match.evidences().get(0);

        assertTrue(evidence.excerpt().contains("regra de validação de CFOP"));
        assertTrue(evidence.excerpt().length() < contentText.length());
        assertEquals(
                contentText.substring(
                        evidence.startPosition(),
                        evidence.endPosition()
                ),
                evidence.excerpt()
        );
        assertEquals("https://example.com/documento.pdf", evidence.sourceUrl());
    }

    @Test
    void naoDeveInferirNfeSemSuporteTextualNoContextoDaEvidence() {
        RuleMatch match = rule.evaluate(contextoComDocumento(
                "A publicação altera regra de validação de CFOP."
        )).orElseThrow();

        assertEquals(
                "Documento fiscal",
                match.technicalImpacts().get(0).affectedComponent()
        );
    }

    @Test
    void deveGerarMatchAPartirDeFiscalChangeCfop() {
        FiscalChange fiscalChange = fiscalChange(
                "I08-140",
                "NF-e modelo 55",
                List.of("1.949", "2.949")
        );

        RuleMatch match = rule.evaluate(contextoComFiscalChanges(
                "Texto sem gatilho textual de CFOP.",
                List.of(fiscalChange)
        )).orElseThrow();

        assertEquals("cfop-validation", match.ruleId());
        assertEquals(1, match.technicalImpacts().size());
        assertEquals(
                "Revisar regra de validação de CFOP",
                match.technicalImpacts().getFirst().title()
        );
        assertEquals(
                "A publicação oficial possui alteração de regra de validação "
                        + "relacionada a CFOP na regra I08-140.",
                match.technicalImpacts().getFirst().description()
        );
        assertEquals("Fiscal", match.technicalImpacts().getFirst().affectedArea());
        assertEquals(
                "NF-e modelo 55",
                match.technicalImpacts().getFirst().affectedComponent()
        );
        assertEquals(ImpactLevel.MEDIUM, match.technicalImpacts().getFirst().impactLevel());
    }

    @Test
    void deveGerarActionItemComRuleCodeEReferencedValuesDoFiscalChange() {
        FiscalChange fiscalChange = fiscalChange(
                "I08-140",
                "NF-e modelo 55",
                List.of("1.949", "2.949")
        );

        RuleMatch match = rule.evaluate(contextoComFiscalChanges(
                "Texto sem valores fiscais.",
                List.of(fiscalChange)
        )).orElseThrow();

        assertEquals(2, match.actionItems().size());
        assertEquals("Revisar validação de CFOP", match.actionItems().getFirst().title());
        assertEquals(
                "Verificar implementação da regra I08-140 para os CFOP "
                        + "1.949 e 2.949.",
                match.actionItems().getFirst().description()
        );
        assertEquals(
                "Verificar regras fiscais correspondentes no ERP",
                match.actionItems().get(1).title()
        );
    }

    @Test
    void deveReutilizarEvidenceDoFiscalChange() {
        FiscalChange fiscalChange = fiscalChange(
                "I08-140",
                "NF-e modelo 55",
                List.of("1.949", "2.949")
        );

        RuleMatch match = rule.evaluate(contextoComFiscalChanges(
                "A publicação altera regra de validação de CFOP textual.",
                List.of(fiscalChange)
        )).orElseThrow();

        assertEquals(1, match.evidences().size());
        assertSame(fiscalChange.evidence(), match.evidences().getFirst());
    }

    @Test
    void deveGerarImpactoComFiscalChangeCfopSemRuleCodeSemInventarCodigo() {
        FiscalChange fiscalChange = fiscalChange(
                null,
                "NF-e modelo 55",
                List.of("1.949", "2.949")
        );

        RuleMatch match = rule.evaluate(contextoComFiscalChanges(
                "Texto sem gatilho textual.",
                List.of(fiscalChange)
        )).orElseThrow();

        assertTrue(match.technicalImpacts().getFirst().description()
                .contains("relacionada a CFOP."));
        assertFalse(match.technicalImpacts().getFirst().description()
                .contains("I08-140"));
        assertEquals(
                "Revisar implementação das validações de CFOP afetadas "
                        + "pela publicação.",
                match.actionItems().getFirst().description()
        );
    }

    @Test
    void deveGerarImpactoComFiscalChangeCfopSemReferencedValuesSemInventarValores() {
        FiscalChange fiscalChange = fiscalChange(
                "I08-140",
                "NF-e modelo 55",
                List.of()
        );

        RuleMatch match = rule.evaluate(contextoComFiscalChanges(
                "Texto sem valores fiscais.",
                List.of(fiscalChange)
        )).orElseThrow();

        assertEquals(
                "Verificar implementação da regra I08-140 relacionada a CFOP.",
                match.actionItems().getFirst().description()
        );
        assertFalse(match.actionItems().getFirst().description().contains("1.949"));
        assertFalse(match.actionItems().getFirst().description().contains("2.949"));
    }

    @Test
    void deveUsarDocumentoFiscalQuandoFiscalChangeCfopNaoTrouxerAffectedDocument() {
        FiscalChange fiscalChange = fiscalChange(
                "I08-140",
                null,
                List.of("1.949", "2.949")
        );

        RuleMatch match = rule.evaluate(contextoComFiscalChanges(
                "Texto sem documento fiscal explicito.",
                List.of(fiscalChange)
        )).orElseThrow();

        assertEquals(
                "Documento fiscal",
                match.technicalImpacts().getFirst().affectedComponent()
        );
    }

    @Test
    void naoDeveConsumirFiscalChangeDeOutroTipoComoMudancaCfop() {
        FiscalChange fiscalChange = new FiscalChange(
                null,
                "I08-140",
                "CFOP",
                "NF-e modelo 55",
                List.of("1.949", "2.949"),
                "Mudança sem tipo suportado.",
                evidence()
        );

        Optional<RuleMatch> result = rule.evaluate(contextoComFiscalChanges(
                "Texto sem regra de validação.",
                List.of(fiscalChange)
        ));

        assertFalse(result.isPresent());
    }

    @Test
    void deveUsarFallbackTextualQuandoFiscalChangesEstiverVazio() {
        RuleMatch match = rule.evaluate(contextoComDocumento(
                "A publicação altera regra de validação de CFOP aplicada à NF-e."
        )).orElseThrow();

        assertEquals(
                "A publicação oficial indica alteração relacionada "
                        + "à regra de validação de CFOP.",
                match.technicalImpacts().getFirst().description()
        );
        assertEquals(ImpactLevel.LOW, match.technicalImpacts().getFirst().impactLevel());
    }

    @Test
    void naoDeveExecutarFallbackTextualQuandoFiscalChangeCfopExistir() {
        FiscalChange fiscalChange = fiscalChange(
                "I08-140",
                "NF-e modelo 55",
                List.of("1.949", "2.949")
        );

        RuleMatch match = rule.evaluate(contextoComFiscalChanges(
                "A publicação altera regra de validação de CFOP aplicada à NF-e.",
                List.of(fiscalChange)
        )).orElseThrow();

        assertEquals(1, match.technicalImpacts().size());
        assertEquals(1, match.evidences().size());
        assertSame(fiscalChange.evidence(), match.evidences().getFirst());
        assertEquals(
                "A publicação oficial possui alteração de regra de validação "
                        + "relacionada a CFOP na regra I08-140.",
                match.technicalImpacts().getFirst().description()
        );
    }

    @Test
    void deveUsarPrimeiroFiscalChangeCfopCompativelSemMisturarDados() {
        FiscalChange firstChange = fiscalChange(
                "I08-140",
                "NF-e modelo 55",
                List.of("1.949", "2.949")
        );
        FiscalChange secondChange = fiscalChange(
                "I99-999",
                "Documento fiscal diverso",
                List.of("5.949", "6.949")
        );

        RuleMatch match = rule.evaluate(contextoComFiscalChanges(
                "Texto sem gatilho textual.",
                List.of(firstChange, secondChange)
        )).orElseThrow();

        assertTrue(match.actionItems().getFirst().description().contains("I08-140"));
        assertTrue(match.actionItems().getFirst().description().contains("1.949"));
        assertFalse(match.actionItems().getFirst().description().contains("I99-999"));
        assertFalse(match.actionItems().getFirst().description().contains("5.949"));
        assertSame(firstChange.evidence(), match.evidences().getFirst());
    }

    private FiscalAnalysisContext contextoComDocumento(String contentText) {
        return contextoComFiscalChanges(contentText, List.of());
    }

    private FiscalAnalysisContext contextoComFiscalChanges(
            String contentText,
            List<FiscalChange> fiscalChanges
    ) {
        return new FiscalAnalysisContext(new DocumentContext(
                "Nota Técnica",
                null,
                DocumentType.NOTA_TECNICA,
                "https://example.com/publicacao.pdf",
                contentText,
                "https://example.com/documento.pdf",
                true
        ), fiscalChanges);
    }

    private FiscalChange fiscalChange(
            String ruleCode,
            String affectedDocument,
            List<String> referencedValues
    ) {
        return new FiscalChange(
                FiscalChangeType.VALIDATION_RULE_CHANGE,
                ruleCode,
                "CFOP",
                affectedDocument,
                referencedValues,
                "Alteração da regra de validação.",
                evidence()
        );
    }

    private EvidenceResult evidence() {
        return new EvidenceResult(
                "Nota Técnica",
                "https://example.com/documento.pdf",
                null,
                null,
                "Esta Nota Técnica altera a regra de validação I08-140.",
                10,
                67
        );
    }
}
