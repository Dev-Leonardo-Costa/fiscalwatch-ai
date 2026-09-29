package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.CfopValidationRule;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.DocumentContext;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.RuleMatch;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private DocumentContext contextoComDocumento(String contentText) {
        return new DocumentContext(
                "Nota Técnica",
                null,
                DocumentType.NOTA_TECNICA,
                "https://example.com/publicacao.pdf",
                contentText,
                "https://example.com/documento.pdf",
                true
        );
    }
}
