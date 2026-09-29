package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import java.util.List;
import java.util.Optional;

public class FiscalRuleEngine {

    private final List<FiscalRule> rules;

    public FiscalRuleEngine(List<FiscalRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<RuleMatch> evaluate(DocumentContext context) {
        return rules.stream()
                .map(rule -> rule.evaluate(context))
                .flatMap(Optional::stream)
                .toList();
    }
}
