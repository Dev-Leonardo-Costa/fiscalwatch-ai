package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NegationDetector {

    private static final Pattern CLAUSE_BOUNDARY = Pattern.compile(
            "[.!?;](?=\\s|$)|\\n\\s*\\n|\\b(?:mas|porém|porem|contudo|entretanto)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NEGATION = Pattern.compile(
            "\\b(?:nao|nem|nenhum[ao]?|inalterad[oa]s?|(?:sem|ausencia\\s+de)\\s+"
                    + "(?:alterac(?:ao|oes)|modificac(?:ao|oes)|mudancas?))\\b"
    );

    public boolean isNegated(String text, String term) {
        Matcher matcher = Pattern.compile(Pattern.quote(term),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        return matcher.find() && isNegatedAt(text, matcher.start());
    }

    public boolean isNegatedAt(String text, int termPosition) {
        TextRange range = clauseAt(text, termPosition);
        // Normaliza somente depois de delimitar no texto original: offsets não
        // dependem da remoção de acentos ou de caracteres combinantes.
        return NEGATION.matcher(normalize(text.substring(range.start(), range.end()))).find();
    }

    public TextRange clauseAt(String text, int position) {
        if (position < 0 || position >= text.length()) {
            throw new IllegalArgumentException("Posição fora do texto");
        }
        int start = 0;
        Matcher boundaries = CLAUSE_BOUNDARY.matcher(text);
        while (boundaries.find()) {
            if (boundaries.end() <= position) {
                start = boundaries.end();
            } else if (boundaries.start() > position) {
                return new TextRange(start, boundaries.start());
            }
        }
        return new TextRange(start, text.length());
    }

    public record TextRange(int start, int end) { }

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
