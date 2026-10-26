package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.DocumentContext;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.EnvironmentDeadlineRule;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.FiscalAnalysisContext;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.RuleMatch;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentDeadlineRuleTest {

    @Test
    void naoDeveTratarExemploOuSimulacaoComoImplantacao() {
        for (String text : List.of("Exemplo: implantação em produção em 15/11/2026.",
                "Simulação de implantação em homologação em 01/10/2026.")) {
            assertTrue(rule.evaluate(contexto(text)).isEmpty(), text);
        }
    }

    @Test
    void cronogramaDaPublicacao90HistoricoNaoConfirmaVigencia() {
        var historicalRule = new EnvironmentDeadlineRule(java.time.Clock.fixed(
                java.time.Instant.parse("2026-10-11T12:00:00Z"), java.time.ZoneOffset.UTC));
        var result = historicalRule.evaluate(contexto("Nota Técnica 2026.009 v.1.00",
                "Cronograma\nTeste\nProdução\n1.00 Alteração I08-140 Até 17/09/2026 Até 17/09/2026")).orElseThrow();
        assertNull(result.homologationDeadline());
        assertNull(result.productionDeadline());
        assertEquals(2, result.evidences().size());
        assertTrue(result.evidences().stream().allMatch(e -> e.documentSection().contains("vigência não confirmada")));
        assertTrue(result.evidences().stream().anyMatch(e -> e.documentSection().contains("homologação")));
        assertTrue(result.evidences().stream().anyMatch(e -> e.documentSection().contains("produção")));
    }

    private final EnvironmentDeadlineRule rule = new EnvironmentDeadlineRule(
            java.time.Clock.fixed(java.time.Instant.parse("2026-01-01T12:00:00Z"), java.time.ZoneOffset.UTC));

    @Test
    void deveExtrairPrazoDeHomologacao() {
        Optional<RuleMatch> result = rule.evaluate(contexto(
                "Implantação no ambiente de homologação em 01/10/2026."
        ));

        assertTrue(result.isPresent());
        assertEquals(
                LocalDateTime.of(2026, 10, 1, 0, 0),
                result.orElseThrow().homologationDeadline()
        );
        assertNull(result.orElseThrow().productionDeadline());
    }

    @Test
    void deveExtrairPrazoDeProducao() {
        Optional<RuleMatch> result = rule.evaluate(contexto(
                "Entrada em ambiente de produção em 15/11/2026."
        ));

        assertTrue(result.isPresent());
        assertEquals(
                LocalDateTime.of(2026, 11, 15, 0, 0),
                result.orElseThrow().productionDeadline()
        );
        assertNull(result.orElseThrow().homologationDeadline());
    }

    @Test
    void deveIgnorarDataIsolada() {
        Optional<RuleMatch> result = rule.evaluate(contexto(
                "Publicação divulgada em 15/11/2026."
        ));

        assertFalse(result.isPresent());
    }

    @Test
    void deveIgnorarDataDeVersaoSemContextoDeAmbiente() {
        Optional<RuleMatch> result = rule.evaluate(contexto(
                "Versão 1.00 de 15/11/2026 da Nota Técnica."
        ));

        assertFalse(result.isPresent());
    }

    @Test
    void deveExtrairCronogramaEstruturadoRealDaNt2026009() {
        Optional<RuleMatch> result = rule.evaluate(contexto(
                "Histórico de Alterações / Cronograma\n"
                        + "Versão Histórico de atualizações Implantação\n"
                        + "Teste\n"
                        + "Implantação\n"
                        + "Produção\n"
                        + "1.00 Alteração da regra de validação I08-140, "
                        + "ampliando a aceitação dos CFOP\n"
                        + "1.949 e 2.949 para as situações previstas na regra. "
                        + "Até 17/09/2026 Até 17/09/2026"
        ));

        assertTrue(result.isPresent());
        assertEquals(
                LocalDateTime.of(2026, 9, 17, 0, 0),
                result.orElseThrow().homologationDeadline()
        );
        assertEquals(
                LocalDateTime.of(2026, 9, 17, 0, 0),
                result.orElseThrow().productionDeadline()
        );
    }

    @Test
    void naoDeveAssociarDuasDatasQuaisquerAutomaticamente() {
        Optional<RuleMatch> result = rule.evaluate(contexto(
                "Publicação em 01/10/2026 e revisão em 15/11/2026."
        ));

        assertFalse(result.isPresent());
    }

    @Test
    void naoDeveUsarTesteEProducaoDistantesForaDeCronograma() {
        Optional<RuleMatch> result = rule.evaluate(contexto(
                "O ambiente de teste será monitorado."
                        + "X".repeat(800)
                        + "O ambiente de produção será comunicado."
                        + "X".repeat(800)
                        + "As datas 01/10/2026 e 15/11/2026 aparecem sem "
                        + "contexto operacional próximo."
        ));

        assertFalse(result.isPresent());
    }

    @Test
    void naoDeveInventarDoisPrazosComUmaDataEmCronograma() {
        Optional<RuleMatch> result = rule.evaluate(contexto(
                "Histórico de Alterações / Cronograma\n"
                        + "Implantação\n"
                        + "Teste\n"
                        + "Implantação\n"
                        + "Produção\n"
                        + "Até 17/09/2026"
        ));

        assertFalse(result.isPresent());
    }

    @Test
    void deveSepararAmbientesNaMesmaLinha() {
        var result = rule.evaluate(contexto("Homologação em 01/10/2026; produção em 15/11/2026.")).orElseThrow();
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), result.homologationDeadline());
        assertEquals(LocalDateTime.of(2026, 11, 15, 0, 0), result.productionDeadline());
        assertEquals(2, result.evidences().size());
    }

    @Test
    void deveRegistrarSomenteLinhaDaVersaoDeclaradaSemPromoverHistorico() {
        String text = "Versão 1.21 – maio de 2023\nSumário\nCronograma ........ 3\n"
                + "Histórico de Alterações / Cronograma\nVersão Implantação\nTeste\nImplantação\nProdução\n"
                + "1.00 Implantação da etapa 1 desta NT 04/07/2022 12/09/2022\n"
                + "Implantação da etapa 2 desta NT 06/03/2023 12/06/2023\n"
                + "1.20 Novos grupos de NCM 03/04/2023 01/06/2023\n"
                + "1.21 Adiada a implantação em produção por 30 dias\n-x- 03/07/2023\n";
        var result = rule.evaluate(contexto(text)).orElseThrow();
        assertNull(result.homologationDeadline());
        assertNull(result.productionDeadline());
        assertEquals(1, result.evidences().size());
        var evidence = result.evidences().getFirst();
        assertTrue(evidence.documentSection().contains("1.21"));
        assertTrue(evidence.documentSection().contains("produção"));
        assertTrue(evidence.documentSection().contains("histórico"));
        assertTrue(evidence.excerpt().contains("-x- 03/07/2023"));
        assertEquals(evidence.excerpt(), text.substring(evidence.startPosition(), evidence.endPosition()));
    }

    @Test
    void naoDeveResolverMultiplasEtapasComoPrazoUnico() {
        assertTrue(rule.evaluate(contexto("Cronograma\nTeste\nProdução\n"
                + "1.00 Etapa 1 01/10/2026 15/11/2026\n"
                + "Etapa 2 01/12/2026 15/12/2026")).isEmpty());
        assertTrue(rule.evaluate(contexto("Cronograma\nTeste\nProdução\n"
                + "1.00 Primeiro grupo 01/10/2026 15/11/2026\n"
                + "Segundo grupo 01/12/2026 15/12/2026")).isEmpty());
    }

    @Test
    void deveManterMarcadorSemAlteracaoSemHerdarData() {
        var result = rule.evaluate(contexto("Versão 1.20\nCronograma\nTeste\nProdução\n"
                + "1.00 Implantação 01/10/2026 15/11/2026\n"
                + "1.20 Implantação Sem alteração 15/12/2026")).orElseThrow();
        assertNull(result.homologationDeadline());
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), result.productionDeadline());
    }

    @Test
    void naoDeveNormalizarDataInvalidaNemAssociarPublicacao() {
        assertTrue(rule.evaluate(contexto("Homologação em 31/02/2026.")).isEmpty());
        assertTrue(rule.evaluate(contexto("Publicação em 01/10/2026 sobre ambiente de produção.")).isEmpty());
    }

    private FiscalAnalysisContext contexto(String contentText) {
        return contexto("Nota Técnica", contentText);
    }

    private FiscalAnalysisContext contexto(String title, String contentText) {
        return new FiscalAnalysisContext(new DocumentContext(
                title,
                null,
                DocumentType.NOTA_TECNICA,
                "https://example.com/publicacao.pdf",
                contentText,
                "https://example.com/documento.pdf",
                true
        ), List.of());
    }

    @Test
    void deveLerTextoRealArmazenadoDaPublicacao94SemUsarIndiceOuHistoricoAnterior() throws Exception {
        String text;
        try (var stream = getClass().getResourceAsStream("/deadlines/nt2021-003-v1-21-extracted-prefix.txt")) {
            text = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var result = rule.evaluate(contexto("Publicada a versão 1.21 da NT 2021.003 da NF-e, que trata da validação do GTIN.", text)).orElseThrow();
        assertNull(result.homologationDeadline());
        assertNull(result.productionDeadline());
        assertEquals(1, result.evidences().size());
        var evidence = result.evidences().getFirst();
        assertTrue(evidence.excerpt().startsWith("1.21 Adiada"));
        assertTrue(evidence.excerpt().endsWith("-x- 03/07/2023"));
        assertEquals(evidence.excerpt(), text.substring(evidence.startPosition(), evidence.endPosition()));
        assertEquals("https://example.com/documento.pdf", evidence.sourceUrl());
    }

    @Test
    void naoDeveSelecionarVersaoMaisRecenteSemVersaoDeclarada() {
        assertTrue(rule.evaluate(contexto("Cronograma\nTeste\nProdução\n"
                + "1.00 Implantação 01/10/2026 15/11/2026\n"
                + "1.20 Implantação 01/12/2026 15/12/2026")).isEmpty());
    }

    @Test
    void deveGuardarReferenciaDeEtapaQuandoUnica() {
        var result = rule.evaluate(contexto("Cronograma\nTeste\nProdução\n"
                + "1.00 Implantação da etapa 2 01/10/2026 15/11/2026")).orElseThrow();
        assertTrue(result.evidences().stream().allMatch(evidence -> evidence.documentSection().contains("etapa 2")));
    }

    @Test
    void naoDeveExtrairDataNegadaOuDataDePublicacaoMesmoComAmbiente() {
        for (String text : List.of("Não haverá implantação em produção em 15/11/2026.",
                "Implantação em produção em 15/11/2026 foi cancelada.",
                "Publicação em produção em 15/11/2026.",
                "Se a produção em 15/11/2026 for aprovada, será comunicada.")) {
            assertTrue(rule.evaluate(contexto(text)).isEmpty(), text);
        }
    }

    @Test
    void naoDeveHerdarCelulaAusenteNemInferirMarcadores() {
        for (String cells : List.of("-x- -x-", "Sem alteração Sem alteração", "15/11/2026")) {
            assertTrue(rule.evaluate(contexto("Cronograma\nTeste\nProdução\n1.00 Implantação " + cells)).isEmpty());
        }
    }

    @Test
    void naoDeveMisturarVersoesConflitantesDoTituloEDocumento() {
        assertTrue(rule.evaluate(contexto("Versão 1.20", "Versão 1.21\nCronograma\nTeste\nProdução\n"
                + "1.21 Implantação 01/10/2026 15/11/2026")).isEmpty());
    }

    @Test
    void devePreservarEvidenciaMasNaoExporDataHistoricaComoPrazoAtual() {
        var result = rule.evaluate(contexto("Implantação em produção em 15/11/2023.")).orElseThrow();
        assertNull(result.productionDeadline());
        assertTrue(result.evidences().getFirst().documentSection().contains("histórico"));
    }
}
