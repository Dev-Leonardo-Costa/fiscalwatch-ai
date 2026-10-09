package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChange;
import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChangeType;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ActionItemResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.TechnicalImpactResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CfopValidationRule implements FiscalRule {

    private static final String RULE_ID = "cfop-validation";
    private static final String AFFECTED_ELEMENT = "CFOP";
    private static final int COMPONENT_CLASSIFICATION_CONTEXT_CHARS = 180;
    private static final int EVIDENCE_CONTEXT_CHARS = 80;
    // O objeto da alteração deve ser a validação de CFOP. Uma regra de GTIN
    // que apenas usa CFOP como condição não satisfaz esta expressão.
    private static final Pattern SPECIFIC_CFOP_CHANGE = Pattern.compile(
            "\\b(?:altera(?:ção|cao)|alter(?:a|am|ou)|modifica(?:ção|cao)|"
                    + "modific(?:a|am|ou)|revis(?:ão|ao))\\s+"
                    + "(?:(?:a|as|da|das|de)\\s+)?"
                    + "(?:regras?\\s+de\\s+)?valida(?:ção|cao|ções|coes)\\s+"
                    + "(?:de|do|dos|para)\\s+(?:(?:o|os)\\s+)?cfop\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern NEGATION_BEFORE_CHANGE = Pattern.compile(
            "\\b(?:não|nao|sem|nenhuma?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final List<String> NFE_TERMS = List.of(
            "nf-e",
            "nota fiscal eletrônica",
            "nota fiscal eletronica"
    );

    @Override
    public Optional<RuleMatch> evaluate(FiscalAnalysisContext context) {
        Optional<RuleMatch> fiscalChangeMatch =
                firstCfopValidationChange(context)
                        .map(this::fromFiscalChange);

        if (fiscalChangeMatch.isPresent()) {
            return fiscalChangeMatch;
        }

        DocumentContext documentContext = context.documentContext();
        Optional<TextChange> change = specificTextChange(documentContext);
        if (change.isEmpty()) {
            return Optional.empty();
        }

        TextChange matchedChange = change.get();
        EvidenceResult evidence = matchedChange.evidence();
        String affectedComponent = resolveAffectedComponent(
                matchedChange.sourceText(),
                matchedChange.position()
        );

        return Optional.of(new RuleMatch(
                RULE_ID,
                List.of(new TechnicalImpactResult(
                        "Revisar regra de validação de CFOP",
                        "A publicação oficial indica alteração relacionada "
                                + "à regra de validação de CFOP.",
                        "Fiscal",
                        affectedComponent == null
                                ? "Documento fiscal"
                                : affectedComponent,
                        ImpactLevel.LOW
                )),
                List.of(
                        new ActionItemResult(
                                "Revisar validação de CFOP",
                                "Verificar a implementação das validações de "
                                        + "CFOP afetadas pela publicação.",
                                "Analista fiscal",
                                ImpactLevel.LOW,
                                null
                        ),
                        new ActionItemResult(
                                "Verificar regras fiscais correspondentes no ERP",
                                "Conferir se as regras fiscais do ERP estão "
                                        + "compatíveis com a publicação oficial.",
                                "Analista fiscal",
                                ImpactLevel.LOW,
                                null
                        )
                ),
                List.of(evidence),
                null,
                null
        ));
    }

    private Optional<FiscalChange> firstCfopValidationChange(
            FiscalAnalysisContext context
    ) {
        return context.fiscalChanges().stream()
                .filter(this::isCfopValidationChange)
                .findFirst();
    }

    private boolean isCfopValidationChange(FiscalChange fiscalChange) {
        return fiscalChange != null
                && fiscalChange.changeType()
                        == FiscalChangeType.VALIDATION_RULE_CHANGE
                && fiscalChange.affectedElement() != null
                && AFFECTED_ELEMENT.equalsIgnoreCase(
                        fiscalChange.affectedElement()
                );
    }

    private RuleMatch fromFiscalChange(FiscalChange fiscalChange) {
        return new RuleMatch(
                RULE_ID,
                List.of(new TechnicalImpactResult(
                        "Revisar regra de validação de CFOP",
                        structuredImpactDescription(fiscalChange),
                        "Fiscal",
                        affectedComponent(fiscalChange),
                        ImpactLevel.MEDIUM
                )),
                structuredActionItems(fiscalChange),
                fiscalChange.evidence() == null
                        ? List.of()
                        : List.of(fiscalChange.evidence()),
                null,
                null
        );
    }

    private String structuredImpactDescription(FiscalChange fiscalChange) {
        StringBuilder description = new StringBuilder(
                "A publicação oficial possui alteração de regra de validação "
                        + "relacionada a CFOP"
        );

        if (hasText(fiscalChange.ruleCode())) {
            description.append(" na regra ").append(fiscalChange.ruleCode());
        }

        description.append('.');

        return description.toString();
    }

    private String affectedComponent(FiscalChange fiscalChange) {
        if (hasText(fiscalChange.affectedDocument())) {
            return fiscalChange.affectedDocument();
        }

        return "Documento fiscal";
    }

    private List<ActionItemResult> structuredActionItems(
            FiscalChange fiscalChange
    ) {
        List<ActionItemResult> actionItems = new ArrayList<>();

        actionItems.add(new ActionItemResult(
                "Revisar validação de CFOP",
                structuredActionDescription(fiscalChange),
                "Analista fiscal",
                ImpactLevel.MEDIUM,
                null
        ));
        actionItems.add(new ActionItemResult(
                "Verificar regras fiscais correspondentes no ERP",
                "Conferir se as regras fiscais do ERP estão compatíveis "
                        + "com a publicação oficial.",
                "Analista fiscal",
                ImpactLevel.MEDIUM,
                null
        ));

        return List.copyOf(actionItems);
    }

    private String structuredActionDescription(FiscalChange fiscalChange) {
        if (hasText(fiscalChange.ruleCode())
                && !fiscalChange.referencedValues().isEmpty()) {
            return "Verificar implementação da regra "
                    + fiscalChange.ruleCode()
                    + " para os CFOP "
                    + joinValues(fiscalChange.referencedValues())
                    + ".";
        }

        if (hasText(fiscalChange.ruleCode())) {
            return "Verificar implementação da regra "
                    + fiscalChange.ruleCode()
                    + " relacionada a CFOP.";
        }

        return "Revisar implementação das validações de CFOP afetadas "
                + "pela publicação.";
    }

    private String joinValues(List<String> values) {
        if (values.size() == 1) {
            return values.getFirst();
        }

        return String.join(
                " e ",
                values
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Optional<TextChange> specificTextChange(DocumentContext context) {
        if (context.documentExtracted()) {
            Optional<TextChange> change = specificTextChange(
                    context, context.documentContentText(), true);
            if (change.isPresent()) {
                return change;
            }
        }
        // Não junta campos independentes para criar uma afirmação inexistente.
        Optional<TextChange> titleChange = specificTextChange(context, context.title(), false);
        return titleChange.isPresent() ? titleChange
                : specificTextChange(context, context.description(), false);
    }

    private Optional<TextChange> specificTextChange(
            DocumentContext context, String sourceText, boolean document
    ) {
        if (!hasText(sourceText)) {
            return Optional.empty();
        }
        Matcher matcher = SPECIFIC_CFOP_CHANGE.matcher(sourceText);
        while (matcher.find()) {
            int statementStart = Math.max(sourceText.lastIndexOf('.', matcher.start() - 1),
                    Math.max(sourceText.lastIndexOf(';', matcher.start() - 1),
                            Math.max(sourceText.lastIndexOf('!', matcher.start() - 1),
                                    sourceText.lastIndexOf('?', matcher.start() - 1)))) + 1;
            String prefix = sourceText.substring(
                    Math.max(statementStart, matcher.start() - EVIDENCE_CONTEXT_CHARS), matcher.start());
            if (NEGATION_BEFORE_CHANGE.matcher(prefix).find()) {
                continue;
            }

            int start = Math.max(0, matcher.start() - EVIDENCE_CONTEXT_CHARS);
            int end = Math.min(sourceText.length(), matcher.end() + EVIDENCE_CONTEXT_CHARS);
            EvidenceResult evidence = new EvidenceResult(
                    context.title(),
                    document ? context.documentSourceUrlOrFallback() : context.publicationDownloadUrl(),
                    null, null, sourceText.substring(start, end),
                    document ? start : null, document ? end : null);
            return Optional.of(new TextChange(sourceText, matcher.start(), evidence));
        }
        return Optional.empty();
    }

    private String resolveAffectedComponent(
            String text,
            int ruleOccurrencePosition
    ) {
        if (ruleOccurrencePosition < 0) {
            return null;
        }

        int start = Math.max(
                0,
                ruleOccurrencePosition - COMPONENT_CLASSIFICATION_CONTEXT_CHARS
        );
        int end = Math.min(
                text.length(),
                ruleOccurrencePosition + COMPONENT_CLASSIFICATION_CONTEXT_CHARS
        );
        String classificationWindow = text
                .substring(start, end)
                .toLowerCase(Locale.ROOT);

        if (NFE_TERMS.stream().anyMatch(classificationWindow::contains)) {
            return "NF-e";
        }

        return null;
    }

    private record TextChange(String sourceText, int position, EvidenceResult evidence) { }
}
