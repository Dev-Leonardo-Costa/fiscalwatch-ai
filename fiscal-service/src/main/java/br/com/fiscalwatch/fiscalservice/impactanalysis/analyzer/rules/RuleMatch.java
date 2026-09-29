package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ActionItemResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.TechnicalImpactResult;

import java.time.LocalDateTime;
import java.util.List;

public record RuleMatch(

        String ruleId,
        List<TechnicalImpactResult> technicalImpacts,
        List<ActionItemResult> actionItems,
        List<EvidenceResult> evidences,
        LocalDateTime homologationDeadline,
        LocalDateTime productionDeadline
) {

    public boolean hasImpactContent() {
        return !technicalImpacts.isEmpty()
                || !actionItems.isEmpty()
                || !evidences.isEmpty();
    }
}
