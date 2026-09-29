package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import java.util.Optional;

public interface FiscalRule {

    Optional<RuleMatch> evaluate(DocumentContext context);
}
