package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ActionItemResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.TechnicalImpactResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HIGH documental exige uma afirmação de mudança estrutural, não uma menção. */
public class StructuralChangeRule implements FiscalRule {
    private static final String TARGET = "(?:schemas?|layouts?|leiautes?)\\b";
    private static final Pattern CHANGE = Pattern.compile(
            "\\b(?:alter(?:a|am|ou)|modific(?:a|am|ou))\\s+"
                    + "(?:(?:o|os|a|as|no|nos|na|nas)\\s+)?" + TARGET
                    + "|\\b(?:altera(?:ção|cao|ções|coes)|modifica(?:ção|cao|ções|coes))\\s+"
                    + "(?:do|dos|da|das|de|no|nos|na|nas)\\s+" + TARGET
                    + "|\\b" + TARGET
                    + "(?:\\s+(?:XML|XSD))?(?:\\s+da\\s+NF-e)?\\s+"
                    + "(?:(?:foi|foram|será|sera|serão|serao|é|e|são|sao)\\s+)?"
                    + "(?:alterad[oa]s?|modificad[oa]s?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern INDEX_LEADER = Pattern.compile("\\.{2,}|(?:\\.\\s+){2,}");
    private static final Pattern INDEX_HEADER = Pattern.compile(
            "(?im)^\\s*(?:sumário|sumario|índice(?: de tabelas)?|indice(?: de tabelas)?)\\s*$");
    private static final Pattern NON_ASSERTIVE_PREFIX = Pattern.compile(
            "\\b(?:exemplo|hipot[eé]tic[oa]s?|refer[eê]ncia\\s+informativ[oa]|possibilidade)\\b"
                    + "|^\\s*(?:se|caso)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final NegationDetector negations = new NegationDetector();

    @Override
    public Optional<RuleMatch> evaluate(FiscalAnalysisContext context) {
        DocumentContext document = context.documentContext();
        Optional<EvidenceResult> evidence = document.documentExtracted()
                ? changeEvidence(document, document.documentContentText(), true) : Optional.empty();
        if (evidence.isEmpty()) {
            evidence = changeEvidence(document, document.title(), false);
        }
        if (evidence.isEmpty()) {
            evidence = changeEvidence(document, document.description(), false);
        }
        return evidence.map(found -> new RuleMatch(
                "structural-change",
                List.of(new TechnicalImpactResult(
                        "Revisar alteração de leiaute ou schema",
                        "A fonte afirma uma mudança de leiaute ou schema. Revisar os contratos e componentes afetados.",
                        "Estrutura de documentos fiscais", "Leiaute / schema", ImpactLevel.HIGH)),
                List.of(new ActionItemResult(
                        "Avaliar adequação à mudança estrutural",
                        "Confirmar no documento oficial a mudança identificada e revisar os componentes que usam essa estrutura.",
                        "Desenvolvedor fiscal", ImpactLevel.HIGH, null)),
                List.of(found), null, null));
    }

    private Optional<EvidenceResult> changeEvidence(DocumentContext context, String text, boolean document) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher changes = CHANGE.matcher(text);
        while (changes.find()) {
            var range = negations.clauseAt(text, changes.start());
            if (isIndexEntry(text, changes.start()) || negations.isNegatedAt(text, changes.start())
                    || NON_ASSERTIVE_PREFIX.matcher(text.substring(range.start(), changes.start())).find()
                    || (range.end() < text.length() && text.charAt(range.end()) == '?')) {
                continue;
            }
            int start = range.start();
            int end = range.end();
            while (start < end && Character.isWhitespace(text.charAt(start))) start++;
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
            return Optional.of(new EvidenceResult(context.title(),
                    document ? context.documentSourceUrlOrFallback() : context.publicationDownloadUrl(),
                    null, null, text.substring(start, end), document ? start : null, document ? end : null));
        }
        return Optional.empty();
    }

    private boolean isIndexEntry(String text, int position) {
        int lineStart = text.lastIndexOf('\n', position) + 1;
        int lineEnd = text.indexOf('\n', position);
        if (lineEnd < 0) lineEnd = text.length();
        if (INDEX_LEADER.matcher(text.substring(lineStart, lineEnd)).find()) return true;
        int paragraphStart = text.lastIndexOf("\n\n", position);
        return INDEX_HEADER.matcher(text.substring(paragraphStart < 0 ? 0 : paragraphStart + 2, position)).find();
    }
}
