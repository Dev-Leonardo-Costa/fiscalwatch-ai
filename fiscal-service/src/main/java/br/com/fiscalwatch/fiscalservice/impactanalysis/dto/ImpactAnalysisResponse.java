package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;

import java.time.LocalDateTime;
import java.util.List;

public record ImpactAnalysisResponse(

        Long id,
        Long publicationId,
        String summary,
        ImpactLevel impactLevel,
        AnalysisStatus status,
        String analysisVersion,
        LocalDateTime homologationDeadline,
        LocalDateTime productionDeadline,
        LocalDateTime analyzedAt,
        List<TechnicalImpactResponse> technicalImpacts,
        List<ActionItemResponse> actionItems,
        List<EvidenceResponse> evidences,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
