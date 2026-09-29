package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ActionItemResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.TechnicalImpactResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CfopValidationRule implements FiscalRule {

    private static final String RULE_ID = "cfop-validation";
    private static final int COMPONENT_CLASSIFICATION_CONTEXT_CHARS = 180;
    private static final List<String> EVIDENCE_TERMS = List.of(
            "regra de validação",
            "regra de validacao",
            "validação",
            "validacao",
            "cfop"
    );
    private static final List<String> NFE_TERMS = List.of(
            "nf-e",
            "nota fiscal eletrônica",
            "nota fiscal eletronica"
    );
    private final TextEvidenceExtractor evidenceExtractor;

    public CfopValidationRule() {
        this(new TextEvidenceExtractor());
    }

    CfopValidationRule(TextEvidenceExtractor evidenceExtractor) {
        this.evidenceExtractor = evidenceExtractor;
    }

    @Override
    public Optional<RuleMatch> evaluate(DocumentContext context) {
        String text = context.lowerAnalyzableText();

        if (!text.contains("cfop") || !hasValidationContext(text)) {
            return Optional.empty();
        }

        EvidenceResult evidence = evidenceExtractor
                .firstEvidenceForTerms(context, EVIDENCE_TERMS)
                .orElseGet(() -> fallbackEvidence(context));
        String affectedComponent = resolveAffectedComponent(
                context,
                firstRuleOccurrencePosition(text)
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

    private boolean hasValidationContext(String text) {
        return text.contains("regra de validação")
                || text.contains("regra de validacao")
                || text.contains("validação")
                || text.contains("validacao");
    }

    private EvidenceResult fallbackEvidence(DocumentContext context) {
        String excerpt = context.description() != null
                && !context.description().isBlank()
                ? context.description()
                : context.title();

        return new EvidenceResult(
                context.title(),
                context.publicationDownloadUrl(),
                null,
                null,
                excerpt,
                null,
                null
        );
    }

    private String resolveAffectedComponent(
            DocumentContext context,
            int ruleOccurrencePosition
    ) {
        if (ruleOccurrencePosition < 0) {
            return null;
        }

        String text = context.analyzableText();
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

    private int firstRuleOccurrencePosition(String text) {
        return EVIDENCE_TERMS.stream()
                .mapToInt(text::indexOf)
                .filter(position -> position >= 0)
                .min()
                .orElse(-1);
    }
}
