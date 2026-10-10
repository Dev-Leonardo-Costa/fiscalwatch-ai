package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.NegationDetector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NegationDetectorTest {

    private final NegationDetector detector = new NegationDetector();

    @Test
    void deveReconhecerLeiauteESchemaComoContextoNegado() {
        String text = "Esta Nota Técnica não modifica o leiaute da NF-e "
                + "nem os schemas XML.";

        assertTrue(detector.isNegated(text, "leiaute"));
        assertTrue(detector.isNegated(text, "schemas"));
    }

    @Test
    void naoDeveConsiderarTermoPositivoComoNegado() {
        String text = "Esta Nota Técnica modifica o leiaute da NF-e.";

        assertFalse(detector.isNegated(text, "leiaute"));
    }

    @Test
    void deveConsiderarAusenciaDeAlteracaoDepoisDoTermo() {
        assertTrue(detector.isNegated("O leiaute não sofreu alteração.", "leiaute"));
        assertTrue(detector.isNegated("O schema foi copiado sem alterações.", "schema"));
        assertTrue(detector.isNegated("O layout permanece inalterado.", "layout"));
    }

    @Test
    void deveConsiderarNegacaoDaDeclaracaoInteiraSemLimiteDeOitentaCaracteres() {
        String text = "Não " + "introduz qualquer modificação nas definições existentes, ".repeat(3)
                + "altera o leiaute da NF-e.";
        assertTrue(detector.isNegated(text, "leiaute"));
    }

    @Test
    void naoDeveTransferirNegacaoEntreDeclaracoesOuContrastes() {
        String text = "O leiaute não mudou. Esta NT altera o schema XML.";
        assertFalse(detector.isNegatedAt(text, text.indexOf("schema")));
        text = "Não altera o leiaute, mas modifica o schema XML.";
        assertTrue(detector.isNegated(text, "leiaute"));
        assertFalse(detector.isNegatedAt(text, text.indexOf("schema")));
    }

    @Test
    void naoDeveTratarNegacaoComoParteDeOutraPalavra() {
        String text = "O schema contém o campo denominado renomear.";
        assertFalse(detector.isNegated(text, "schema"));
    }
}
