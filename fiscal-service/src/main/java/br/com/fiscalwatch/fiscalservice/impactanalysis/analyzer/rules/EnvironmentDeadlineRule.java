package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnvironmentDeadlineRule implements FiscalRule {

    private static final String RULE_ID = "environment-deadline";
    private static final int CONTEXT_CHARS = 120;
    private static final int STRUCTURED_SCHEDULE_WINDOW_CHARS = 700;
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{2}/\\d{2}/\\d{4})\\b"
    );
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> HOMOLOGATION_TERMS = List.of(
            "homologação",
            "homologacao",
            "ambiente de testes",
            "teste"
    );
    private static final List<String> PRODUCTION_TERMS = List.of(
            "produção",
            "producao",
            "ambiente de produção",
            "ambiente de producao"
    );

    @Override
    public Optional<RuleMatch> evaluate(DocumentContext context) {
        String text = context.analyzableText();
        Optional<RuleMatch> structuredScheduleMatch =
                evaluateStructuredSchedule(text);

        if (structuredScheduleMatch.isPresent()) {
            return structuredScheduleMatch;
        }

        if (hasStructuredScheduleHeaders(text)) {
            return Optional.empty();
        }

        Matcher matcher = DATE_PATTERN.matcher(text);
        LocalDateTime homologationDeadline = null;
        LocalDateTime productionDeadline = null;

        while (matcher.find()) {
            String dateText = matcher.group(1);
            String surroundingText = surroundingText(
                    text,
                    matcher.start(),
                    matcher.end()
            );
            LocalDateTime deadline = LocalDate
                    .parse(dateText, DATE_FORMATTER)
                    .atStartOfDay();

            if (homologationDeadline == null
                    && containsAny(surroundingText, HOMOLOGATION_TERMS)) {
                homologationDeadline = deadline;
            }

            if (productionDeadline == null
                    && containsAny(surroundingText, PRODUCTION_TERMS)) {
                productionDeadline = deadline;
            }
        }

        if (homologationDeadline == null && productionDeadline == null) {
            return Optional.empty();
        }

        return Optional.of(new RuleMatch(
                RULE_ID,
                List.of(),
                List.of(),
                List.of(),
                homologationDeadline,
                productionDeadline
        ));
    }

    private Optional<RuleMatch> evaluateStructuredSchedule(String text) {
        String lowerText = text.toLowerCase();
        int schedulePosition = firstSchedulePosition(lowerText);

        if (schedulePosition < 0) {
            return Optional.empty();
        }

        int blockEnd = Math.min(
                text.length(),
                schedulePosition + STRUCTURED_SCHEDULE_WINDOW_CHARS
        );
        String block = text.substring(schedulePosition, blockEnd);
        String lowerBlock = block.toLowerCase();
        int testPosition = lowerBlock.indexOf("teste");
        int productionPosition = firstProductionPosition(lowerBlock);

        if (testPosition < 0
                || productionPosition < 0
                || testPosition > productionPosition) {
            return Optional.empty();
        }

        Matcher matcher = DATE_PATTERN.matcher(block);

        if (!matcher.find()) {
            return Optional.empty();
        }

        LocalDateTime homologationDeadline = LocalDate
                .parse(matcher.group(1), DATE_FORMATTER)
                .atStartOfDay();

        if (!matcher.find()) {
            return Optional.empty();
        }

        LocalDateTime productionDeadline = LocalDate
                .parse(matcher.group(1), DATE_FORMATTER)
                .atStartOfDay();

        return Optional.of(new RuleMatch(
                RULE_ID,
                List.of(),
                List.of(),
                List.of(),
                homologationDeadline,
                productionDeadline
        ));
    }

    private boolean hasStructuredScheduleHeaders(String text) {
        String lowerText = text.toLowerCase();
        int schedulePosition = firstSchedulePosition(lowerText);

        if (schedulePosition < 0) {
            return false;
        }

        int blockEnd = Math.min(
                text.length(),
                schedulePosition + STRUCTURED_SCHEDULE_WINDOW_CHARS
        );
        String lowerBlock = text.substring(schedulePosition, blockEnd)
                .toLowerCase();
        int testPosition = lowerBlock.indexOf("teste");
        int productionPosition = firstProductionPosition(lowerBlock);

        return testPosition >= 0
                && productionPosition >= 0
                && testPosition < productionPosition;
    }

    private int firstSchedulePosition(String lowerText) {
        int cronogramaPosition = lowerText.indexOf("cronograma");
        int historicoPosition = lowerText.indexOf("histórico de alterações");
        int historicoSemAcentoPosition = lowerText.indexOf(
                "historico de alteracoes"
        );

        return List.of(
                        cronogramaPosition,
                        historicoPosition,
                        historicoSemAcentoPosition
                )
                .stream()
                .filter(position -> position >= 0)
                .min(Integer::compareTo)
                .orElse(-1);
    }

    private int firstProductionPosition(String lowerText) {
        int producaoPosition = lowerText.indexOf("produção");
        int producaoSemAcentoPosition = lowerText.indexOf("producao");

        return List.of(producaoPosition, producaoSemAcentoPosition)
                .stream()
                .filter(position -> position >= 0)
                .min(Integer::compareTo)
                .orElse(-1);
    }

    private String surroundingText(String text, int start, int end) {
        int contextStart = Math.max(0, start - CONTEXT_CHARS);
        int contextEnd = Math.min(text.length(), end + CONTEXT_CHARS);

        return text.substring(contextStart, contextEnd).toLowerCase();
    }

    private boolean containsAny(String text, List<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }
}
