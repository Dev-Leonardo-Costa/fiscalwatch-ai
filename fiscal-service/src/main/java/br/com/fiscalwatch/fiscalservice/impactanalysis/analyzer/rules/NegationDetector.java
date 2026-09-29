package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public class NegationDetector {

    private static final int LOOKBACK_CHARS = 80;
    private static final List<String> NEGATION_MARKERS = List.of(
            "nao",
            "sem alteracao",
            "nao modifica",
            "nao altera",
            "nao impacta",
            "nao ha alteracao",
            "nem"
    );

    public boolean isNegated(String text, String term) {
        String normalizedText = normalize(text);
        String normalizedTerm = normalize(term);
        int position = normalizedText.indexOf(normalizedTerm);

        if (position < 0) {
            return false;
        }

        return isNegatedAt(text, position);
    }

    public boolean isNegatedAt(String text, int termPosition) {
        String normalizedText = normalize(text);
        int start = Math.max(0, termPosition - LOOKBACK_CHARS);
        String contextBeforeTerm = normalizedText.substring(start, termPosition);

        return NEGATION_MARKERS.stream()
                .anyMatch(contextBeforeTerm::contains);
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(
                value,
                Normalizer.Form.NFD
        );

        return normalized
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
