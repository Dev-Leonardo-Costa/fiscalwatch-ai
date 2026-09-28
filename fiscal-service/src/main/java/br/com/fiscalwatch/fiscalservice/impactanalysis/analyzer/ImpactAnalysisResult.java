package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;

import java.time.LocalDateTime;
import java.util.List;

public record ImpactAnalysisResult(

        String summary,
        ImpactLevel impactLevel,
        LocalDateTime homologationDeadline,
        LocalDateTime productionDeadline,
        List<TechnicalImpactResult> technicalImpacts,
        List<ActionItemResult> actionItems,
        List<EvidenceResult> evidences
) {
}
