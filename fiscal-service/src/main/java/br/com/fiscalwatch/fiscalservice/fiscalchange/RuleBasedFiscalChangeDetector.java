package br.com.fiscalwatch.fiscalservice.fiscalchange;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.DocumentContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleBasedFiscalChangeDetector implements FiscalChangeDetector {

    private static final String RULE_CODE = "I08-140";
    private static final String AFFECTED_ELEMENT = "CFOP";
    private static final int CONTEXT_CHARS = 360;
    private static final Pattern CFOP_VALUES_PATTERN = Pattern.compile(
            "\\b[1-9]\\.\\d{3}\\b"
    );

    @Override
    public List<FiscalChange> detect(DocumentContext context) {
        if (context == null
                || !context.documentExtracted()
                || context.documentContentText() == null
                || context.documentContentText().isBlank()) {
            return List.of();
        }

        return bestCandidate(context)
                .map(List::of)
                .orElseGet(List::of);
    }

    private Optional<FiscalChange> bestCandidate(DocumentContext context) {
        String contentText = context.documentContentText();
        String lowerText = contentText.toLowerCase(Locale.ROOT);
        List<Candidate> candidates = new ArrayList<>();

        int fromIndex = 0;
        while (fromIndex < contentText.length()) {
            int rulePosition = lowerText.indexOf(
                    RULE_CODE.toLowerCase(Locale.ROOT),
                    fromIndex
            );

            if (rulePosition < 0) {
                break;
            }

            Candidate candidate = evaluateCandidate(context, rulePosition);
            if (candidate != null && candidate.score() > 0) {
                candidates.add(candidate);
            }

            fromIndex = rulePosition + RULE_CODE.length();
        }

        return candidates.stream()
                .max((left, right) -> {
                    int scoreComparison = Integer.compare(
                            left.score(),
                            right.score()
                    );
                    if (scoreComparison != 0) {
                        return scoreComparison;
                    }

                    return Integer.compare(
                            right.startPosition(),
                            left.startPosition()
                    );
                })
                .map(Candidate::change);
    }

    private Candidate evaluateCandidate(
            DocumentContext context,
            int rulePosition
    ) {
        String contentText = context.documentContentText();
        int windowStart = Math.max(0, rulePosition - CONTEXT_CHARS);
        int windowEnd = Math.min(
                contentText.length(),
                rulePosition + RULE_CODE.length() + CONTEXT_CHARS
        );
        String window = contentText.substring(windowStart, windowEnd);
        String lowerWindow = window.toLowerCase(Locale.ROOT);

        if (!hasValidationRuleChangeContext(lowerWindow)) {
            return null;
        }

        int evidenceStart = findEvidenceStart(contentText, rulePosition);
        int evidenceEnd = findEvidenceEnd(contentText, rulePosition);
        String excerpt = contentText.substring(evidenceStart, evidenceEnd);
        String lowerExcerpt = excerpt.toLowerCase(Locale.ROOT);

        String affectedElement = hasCfopContext(lowerExcerpt)
                ? AFFECTED_ELEMENT
                : null;
        List<String> referencedValues = affectedElement == null
                ? List.of()
                : referencedCfopValues(excerpt);
        String affectedDocument = resolveAffectedDocument(lowerExcerpt);

        if (affectedElement == null || referencedValues.isEmpty()) {
            return null;
        }

        EvidenceResult evidence = new EvidenceResult(
                context.title(),
                context.documentSourceUrlOrFallback(),
                null,
                null,
                excerpt,
                evidenceStart,
                evidenceEnd
        );

        FiscalChange change = new FiscalChange(
                FiscalChangeType.VALIDATION_RULE_CHANGE,
                RULE_CODE,
                affectedElement,
                affectedDocument,
                referencedValues,
                description(affectedDocument, referencedValues),
                evidence
        );

        return new Candidate(
                change,
                score(lowerWindow, lowerExcerpt, referencedValues),
                evidenceStart
        );
    }

    private boolean hasValidationRuleChangeContext(String lowerText) {
        return hasValidationRuleContext(lowerText)
                && hasChangeContext(lowerText);
    }

    private boolean hasValidationRuleContext(String lowerText) {
        return lowerText.contains("regra de validação")
                || lowerText.contains("regra de validacao");
    }

    private boolean hasChangeContext(String lowerText) {
        return lowerText.contains("altera a regra")
                || lowerText.contains("altera regra")
                || lowerText.contains("alteração da regra")
                || lowerText.contains("alteracao da regra")
                || lowerText.contains("alteração de regra")
                || lowerText.contains("alteracao de regra");
    }

    private boolean hasCfopContext(String lowerText) {
        return lowerText.contains("cfop");
    }

    private List<String> referencedCfopValues(String text) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = CFOP_VALUES_PATTERN.matcher(text);

        while (matcher.find()) {
            if (isNearCfop(lowerText, matcher.start(), matcher.end())) {
                values.add(matcher.group());
            }
        }

        return List.copyOf(values);
    }

    private boolean isNearCfop(String lowerText, int start, int end) {
        int contextStart = Math.max(0, start - 45);
        int contextEnd = Math.min(lowerText.length(), end + 45);

        return lowerText
                .substring(contextStart, contextEnd)
                .contains("cfop");
    }

    private String resolveAffectedDocument(String lowerText) {
        boolean hasNfe = lowerText.contains("nf-e")
                || lowerText.contains("nota fiscal eletrônica")
                || lowerText.contains("nota fiscal eletronica");
        boolean hasModel55 = lowerText.contains("modelo 55");

        if (hasNfe && hasModel55) {
            return "NF-e modelo 55";
        }

        if (hasNfe) {
            return "NF-e";
        }

        return null;
    }

    private String description(
            String affectedDocument,
            List<String> referencedValues
    ) {
        String documentDescription = affectedDocument == null
                ? "documento fiscal"
                : affectedDocument;

        return "Alteração da regra de validação "
                + RULE_CODE
                + " para permitir os CFOP "
                + String.join(" e ", referencedValues)
                + " na "
                + documentDescription
                + ".";
    }

    private int score(
            String lowerWindow,
            String lowerExcerpt,
            List<String> referencedValues
    ) {
        int score = 0;

        if (lowerExcerpt.contains("esta nota técnica")
                || lowerExcerpt.contains("esta nota tecnica")) {
            score += 8;
        }

        if (lowerExcerpt.contains("nf-e")) {
            score += 4;
        }

        if (lowerExcerpt.contains("modelo 55")) {
            score += 4;
        }

        if (lowerExcerpt.contains("cfop")) {
            score += 4;
        }

        score += referencedValues.size() * 3;

        if (lowerWindow.contains("permitir a utilização")
                || lowerWindow.contains("permitir a utilizacao")) {
            score += 3;
        }

        if (lowerWindow.contains("ampliando a aceitação")
                || lowerWindow.contains("ampliando a aceitacao")) {
            score += 2;
        }

        return score;
    }

    private int findEvidenceStart(String contentText, int rulePosition) {
        int paragraphStart = contentText.lastIndexOf("\n\n", rulePosition);
        int lineStart = contentText.lastIndexOf('\n', rulePosition);
        int sentenceStart = contentText.lastIndexOf('.', rulePosition);

        int start = 0;
        if (paragraphStart >= 0) {
            start = Math.max(start, paragraphStart + 2);
        }
        if (lineStart >= 0) {
            start = Math.max(start, lineStart + 1);
        }
        if (sentenceStart >= 0) {
            start = Math.max(start, sentenceStart + 1);
        }

        return firstNonWhitespacePosition(contentText, start);
    }

    private int findEvidenceEnd(String contentText, int rulePosition) {
        int paragraphEnd = contentText.indexOf("\n\n", rulePosition);
        int end = paragraphEnd >= 0 ? paragraphEnd : contentText.length();

        return lastNonWhitespacePosition(contentText, end);
    }

    private int firstNonWhitespacePosition(String text, int start) {
        int position = Math.max(0, start);

        while (position < text.length()
                && Character.isWhitespace(text.charAt(position))) {
            position++;
        }

        return position;
    }

    private int lastNonWhitespacePosition(String text, int end) {
        int position = Math.min(text.length(), end);

        while (position > 0
                && Character.isWhitespace(text.charAt(position - 1))) {
            position--;
        }

        return position;
    }

    private record Candidate(

            FiscalChange change,
            int score,
            int startPosition
    ) {
    }
}
