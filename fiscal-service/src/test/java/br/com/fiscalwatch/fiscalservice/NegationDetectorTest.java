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
}
