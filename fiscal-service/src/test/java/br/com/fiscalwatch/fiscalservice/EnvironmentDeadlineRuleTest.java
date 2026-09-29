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

    private final EnvironmentDeadlineRule rule = new EnvironmentDeadlineRule();

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

    private FiscalAnalysisContext contexto(String contentText) {
        return new FiscalAnalysisContext(new DocumentContext(
                "Nota Técnica",
                null,
                DocumentType.NOTA_TECNICA,
                "https://example.com/publicacao.pdf",
                contentText,
                "https://example.com/documento.pdf",
                true
        ), List.of());
    }
}
