package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChange;
import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChangeType;
import br.com.fiscalwatch.fiscalservice.fiscalchange.RuleBasedFiscalChangeDetector;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.DocumentContext;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedFiscalChangeDetectorTest {

    private static final String SOURCE_URL =
            "https://example.com/NT2026.009_v1.00_Alteracao_RV_CFOP.pdf";

    private final RuleBasedFiscalChangeDetector detector =
            new RuleBasedFiscalChangeDetector();

    @Test
    void deveDetectarFiscalChangeDaNt2026009() {
        String contentText = trechoNormativoNt2026009();

        FiscalChange change = detector.detect(contextoComDocumento(contentText))
                .getFirst();

        assertEquals(
                FiscalChangeType.VALIDATION_RULE_CHANGE,
                change.changeType()
        );
        assertEquals("I08-140", change.ruleCode());
        assertEquals("CFOP", change.affectedElement());
        assertEquals("NF-e modelo 55", change.affectedDocument());
        assertEquals(List.of("1.949", "2.949"), change.referencedValues());
        assertEquals(
                "Alteração da regra de validação I08-140 para permitir "
                        + "os CFOP 1.949 e 2.949 na NF-e modelo 55.",
                change.description()
        );
    }

    @Test
    void deveGerarEvidenceComTrechoNormativoEOffsetsExatos() {
        String contentText = "Cabeçalho\n\n"
                + trechoNormativoNt2026009()
                + "\n\nRodapé";

        FiscalChange change = detector.detect(contextoComDocumento(contentText))
                .getFirst();
        var evidence = change.evidence();

        assertTrue(evidence.excerpt().contains(
                "Esta Nota Técnica altera a regra de validação I08-140"
        ));
        assertTrue(evidence.excerpt().contains("CFOP 1.949 e 2.949"));
        assertFalse(evidence.excerpt().contains("Rodapé"));
        assertTrue(evidence.excerpt().length() < contentText.length());
        assertEquals(SOURCE_URL, evidence.sourceUrl());
        assertEquals("Nota Técnica 2026.009 v.1.00", evidence.sourceTitle());
        assertEquals(
                contentText.substring(
                        evidence.startPosition(),
                        evidence.endPosition()
                ),
                evidence.excerpt()
        );
    }

    @Test
    void devePreferirTrechoNormativoQuandoHaMultiplasOcorrencias() {
        String contentText = "Controle de Versões\n"
                + "1.00 09/2026 Alteração da regra de validação I08-140."
                + "\n\n"
                + "Histórico de Alterações / Cronograma\n"
                + "1.00 Alteração da regra de validação I08-140, ampliando "
                + "a aceitação dos CFOP 1.949 e 2.949 para as situações "
                + "previstas na regra. Até 17/09/2026 Até 17/09/2026"
                + "\n\n"
                + trechoNormativoNt2026009();

        FiscalChange change = detector.detect(contextoComDocumento(contentText))
                .getFirst();

        assertTrue(change.evidence().excerpt().startsWith("Esta Nota Técnica"));
        assertTrue(change.evidence().excerpt().contains("NF-e"));
        assertTrue(change.evidence().excerpt().contains("modelo 55"));
    }

    @Test
    void naoDeveCapturarNumerosForaDoContextoDeCfopComoReferencedValues() {
        String contentText = "Nota Técnica 2026.009 - Versão 1.00\n"
                + "Até 17/09/2026. Modelo 55. Obrig. 327. nItem: 999.\n\n"
                + trechoNormativoNt2026009()
                + "\n\nObrig. 327 Rejeição: CFOP inválido para Nota Fiscal "
                + "de devolução ou de retorno de mercadoria [nItem: 999]";

        FiscalChange change = detector.detect(contextoComDocumento(contentText))
                .getFirst();

        assertEquals(List.of("1.949", "2.949"), change.referencedValues());
    }

    @Test
    void naoDeveDetectarRuleCodeSemContextoDeRegraDeValidacao() {
        String contentText = "Referência interna I08-140 para controle "
                + "documental dos anexos.";

        assertTrue(detector.detect(contextoComDocumento(contentText)).isEmpty());
    }

    @Test
    void naoDeveAssociarCfopDistanteAoFiscalChange() {
        String contentText = "CFOP 1.949 e 2.949 aparecem em uma tabela "
                + "informativa."
                + "X".repeat(500)
                + "Esta Nota Técnica altera a regra de validação I08-140 da "
                + "Nota Fiscal Eletrônica (NF-e), modelo 55.";

        assertTrue(detector.detect(contextoComDocumento(contentText)).isEmpty());
    }

    @Test
    void naoDeveInventarModeloQuandoNfeApareceSemModelo55() {
        String contentText = "Esta Nota Técnica altera a regra de validação "
                + "I08-140 da Nota Fiscal Eletrônica (NF-e), para permitir "
                + "a utilização dos CFOP 1.949 e 2.949 em todas as situações "
                + "abrangidas pela própria regra.";

        FiscalChange change = detector.detect(contextoComDocumento(contentText))
                .getFirst();

        assertEquals("NF-e", change.affectedDocument());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHaMudancaReconhecivel() {
        String contentText = "Documento informativo sobre procedimentos fiscais "
                + "sem alteração de regra reconhecida.";

        assertTrue(detector.detect(contextoComDocumento(contentText)).isEmpty());
    }

    @Test
    void deveRetornarListaVaziaQuandoDocumentoAusentePendenteFalhoOuVazio() {
        assertTrue(detector.detect(new DocumentContext(
                "Nota Técnica",
                null,
                DocumentType.NOTA_TECNICA,
                "https://example.com/publicacao.pdf",
                null,
                SOURCE_URL,
                false
        )).isEmpty());

        assertTrue(detector.detect(new DocumentContext(
                "Nota Técnica",
                null,
                DocumentType.NOTA_TECNICA,
                "https://example.com/publicacao.pdf",
                trechoNormativoNt2026009(),
                SOURCE_URL,
                false
        )).isEmpty());

        assertTrue(detector.detect(new DocumentContext(
                "Nota Técnica",
                null,
                DocumentType.NOTA_TECNICA,
                "https://example.com/publicacao.pdf",
                " ",
                SOURCE_URL,
                true
        )).isEmpty());
    }

    private String trechoNormativoNt2026009() {
        return "Esta Nota Técnica altera a regra de validação I08-140 da "
                + "Nota Fiscal Eletrônica (NF-e), modelo 55, para permitir "
                + "a utilização dos CFOP 1.949 e 2.949 em todas as situações "
                + "abrangidas pela própria regra.";
    }

    private DocumentContext contextoComDocumento(String contentText) {
        return new DocumentContext(
                "Nota Técnica 2026.009 v.1.00",
                null,
                DocumentType.NOTA_TECNICA,
                "https://example.com/publicacao.pdf",
                contentText,
                SOURCE_URL,
                true
        );
    }
}
