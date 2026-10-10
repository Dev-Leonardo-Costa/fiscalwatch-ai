package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Document dates do not establish continuing legal validity. Ambiguous tables require review. */
public class EnvironmentDeadlineRule implements FiscalRule {
    private static final String DATE = "\\d{2}/\\d{2}/\\d{4}";
    private static final String CELL = "(?:Até\\s+)?(?:" + DATE + "|-x-|Sem\\s+altera[çc][ãa]o)";
    private static final Pattern CELLS = Pattern.compile("(" + CELL + ")\\s+(" + CELL + ")\\h*\\r?$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern VERSION = Pattern.compile("(?i)(?:vers[ãa]o\\s+|\\bv\\.?\\s*)(\\d+\\.\\d{2})\\b");
    private static final Pattern ROW = Pattern.compile("(?m)^\\h*(\\d+\\.\\d{2})(?![\\d.])\\h+");
    private static final Pattern STAGE = Pattern.compile("(?i)etapa\\s+(\\d+)");
    private static final Pattern HEADER = Pattern.compile("(?im)^\\h*(?:Hist[óo]rico de Altera[çc][õo]es\\s*/\\s*)?Cronograma\\h*\\r?$"
            + "[\\s\\S]{0,180}?\\bTeste\\b\\s+(?:Implanta[çc][ãa]o\\s+)?Produ[çc][ãa]o\\b");
    private static final Pattern EXPLICIT = Pattern.compile("(?iu)\\b(homologa[çc][ãa]o|teste(?:s)?|produ[çc][ãa]o)\\s+(?:em|até|:)?\\s*(" + DATE + ")\\b");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final Clock clock;

    public EnvironmentDeadlineRule() { this(Clock.system(ZoneId.of("America/Fortaleza"))); }
    public EnvironmentDeadlineRule(Clock clock) { this.clock = clock; }

    @Override
    public Optional<RuleMatch> evaluate(FiscalAnalysisContext context) {
        var document = context.documentContext();
        // Never concatenate sources: offsets refer exclusively to the extracted document.
        if (!document.documentExtracted()) return Optional.empty();
        String text = document.documentContentText();
        var headers = HEADER.matcher(text);
        if (headers.find()) {
            int start = headers.end();
            if (headers.find()) return Optional.empty();
            return schedule(document, text, start);
        }
        if (Pattern.compile("(?iu)cronograma|hist[óo]rico|sum[áa]rio").matcher(text).find()) return Optional.empty();
        List<Candidate> candidates = new ArrayList<>();
        var matcher = EXPLICIT.matcher(text);
        var negation = new NegationDetector();
        while (matcher.find()) {
            var clause = negation.clauseAt(text, matcher.start());
            String clauseText = text.substring(clause.start(), clause.end());
            if (negation.isNegatedAt(text, matcher.start())
                    || Pattern.compile("(?iu)publica[çc][ãa]o|revis[ãa]o|cancelad[oa]|hipot[ée]tic[oa]|\\bse\\b").matcher(clauseText).find()) continue;
            LocalDate date = parse(matcher.group(2));
            if (date != null) candidates.add(new Candidate(matcher.group(1).toLowerCase(java.util.Locale.ROOT).startsWith("produ"), date,
                    clause.start(), clause.end(), "Prazo documental"));
        }
        return result(document, text, candidates);
    }

    private Optional<RuleMatch> schedule(DocumentContext document, String text, int start) {
        String declared = declaredVersion(document.title(), text.substring(0, start));
        if ("ambiguous".equals(declared)) return Optional.empty();
        List<Row> rows = new ArrayList<>();
        var matcher = ROW.matcher(text);
        while (matcher.find(start)) {
            rows.add(new Row(matcher.group(1), matcher.start()));
            start = matcher.end();
        }
        if (rows.isEmpty()) return Optional.empty();
        if (declared == null) {
            if (rows.stream().map(Row::version).distinct().count() != 1) return Optional.empty();
            declared = rows.getFirst().version();
        }
        String selected = declared;
        var selectedRows = rows.stream().filter(row -> row.version().equals(selected)).toList();
        if (selectedRows.size() != 1) return Optional.empty();
        Row row = selectedRows.getFirst();
        int index = rows.indexOf(row);
        int end = index + 1 < rows.size() ? rows.get(index + 1).start() : text.length();
        String block = text.substring(row.start(), end);
        var cells = CELLS.matcher(block);
        if (!cells.find()) return Optional.empty();
        int evidenceEnd = cells.end();
        String remainder = block.substring(evidenceEnd);
        if (Pattern.compile("(?i)^\\s*(?:Implanta[çc][ãa]o da etapa|Etapa\\s+\\d|" + DATE + ")").matcher(remainder).find()) return Optional.empty();
        // A second pair in the same paragraph may be another stage even without a stage label.
        if (CELLS.matcher(remainder.split("\\r?\\n\\s*\\r?\\n", 2)[0]).find()) return Optional.empty();
        block = block.substring(0, evidenceEnd);
        var stages = STAGE.matcher(block);
        List<String> stageValues = new ArrayList<>();
        while (stages.find()) stageValues.add(stages.group(1));
        if (stageValues.stream().distinct().count() > 1) return Optional.empty();
        // A complete terminal pair establishes column association. Missing cells are ambiguous.
        if (Pattern.compile(DATE).matcher(block.substring(0, cells.start())).find()) return Optional.empty();
        List<Candidate> candidates = new ArrayList<>();
        String section = "Cronograma; versão " + selected + (stageValues.isEmpty() ? "" : "; etapa " + stageValues.getFirst());
        for (int column = 1; column <= 2; column++) {
            var dateMatcher = Pattern.compile(DATE).matcher(cells.group(column));
            if (dateMatcher.find()) {
                LocalDate date = parse(dateMatcher.group());
                if (date == null) return Optional.empty();
                candidates.add(new Candidate(column == 2, date, row.start(), row.start() + cells.end(), section));
            }
        }
        return result(document, text, candidates);
    }

    private String declaredVersion(String title, String prefix) {
        List<String> versions = new ArrayList<>();
        var index = Pattern.compile("(?iu)sum[áa]rio").matcher(prefix);
        int coverEnd = index.find() ? index.start() : Math.min(prefix.length(), 500);
        for (String source : List.of(title == null ? "" : title, prefix.substring(0, coverEnd))) {
            var matcher = VERSION.matcher(source);
            while (matcher.find()) versions.add(matcher.group(1));
        }
        var distinct = versions.stream().distinct().toList();
        return distinct.isEmpty() ? null : distinct.size() == 1 ? distinct.getFirst() : "ambiguous";
    }

    private Optional<RuleMatch> result(DocumentContext document, String text, List<Candidate> candidates) {
        LocalDateTime homologation = null;
        LocalDateTime production = null;
        List<EvidenceResult> evidence = new ArrayList<>();
        for (boolean environment : List.of(false, true)) {
            var values = candidates.stream().filter(candidate -> candidate.production() == environment).toList();
            if (values.size() != 1) continue;
            Candidate candidate = values.getFirst();
            boolean historical = candidate.date().isBefore(LocalDate.now(clock));
            if (!historical) {
                if (environment) production = candidate.date().atStartOfDay();
                else homologation = candidate.date().atStartOfDay();
            }
            evidence.add(new EvidenceResult(document.title(), document.documentSourceUrlOrFallback(),
                    candidate.section() + "; " + (environment ? "produção" : "homologação")
                            + (historical ? "; histórico, vigência não confirmada" : "; vigência externa não verificada"),
                    null, text.substring(candidate.start(), candidate.end()), candidate.start(), candidate.end()));
        }
        if (evidence.isEmpty()) return Optional.empty();
        return Optional.of(new RuleMatch("environment-deadline", List.of(), List.of(), evidence, homologation, production));
    }

    private LocalDate parse(String value) {
        try { return LocalDate.parse(value, FORMAT); }
        catch (DateTimeParseException exception) { return null; }
    }
    private record Row(String version, int start) {}
    private record Candidate(boolean production, LocalDate date, int start, int end, String section) {}
}
