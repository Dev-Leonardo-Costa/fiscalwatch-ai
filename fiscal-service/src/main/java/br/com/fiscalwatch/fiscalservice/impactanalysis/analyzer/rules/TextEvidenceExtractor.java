package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class TextEvidenceExtractor {

    private static final int DEFAULT_CONTEXT_CHARS = 80;

    public Optional<EvidenceResult> firstEvidenceForTerms(
            DocumentContext context,
            List<String> terms
    ) {
        if (!context.documentExtracted()) {
            return Optional.empty();
        }

        String contentText = context.documentContentText();
        String lowerText = contentText.toLowerCase(Locale.ROOT);
        int firstPosition = -1;
        String firstTerm = null;

        for (String term : terms) {
            int position = lowerText.indexOf(term.toLowerCase(Locale.ROOT));

            if (position < 0) {
                continue;
            }

            if (firstPosition < 0 || position < firstPosition) {
                firstPosition = position;
                firstTerm = term;
            }
        }

        if (firstPosition < 0) {
            return Optional.empty();
        }

        int startPosition = Math.max(
                0,
                firstPosition - DEFAULT_CONTEXT_CHARS
        );
        int endPosition = Math.min(
                contentText.length(),
                firstPosition + firstTerm.length() + DEFAULT_CONTEXT_CHARS
        );

        return Optional.of(new EvidenceResult(
                context.title(),
                context.documentSourceUrlOrFallback(),
                null,
                null,
                contentText.substring(startPosition, endPosition),
                startPosition,
                endPosition
        ));
    }
}
