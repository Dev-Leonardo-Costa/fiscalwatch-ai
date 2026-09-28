package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record ImpactAnalysisRequest(

        @NotNull
        Long publicationId,

        String summary,

        @NotNull
        ImpactLevel impactLevel,

        @NotNull
        @Size(max = 50)
        String analysisVersion,

        LocalDateTime homologationDeadline,

        LocalDateTime productionDeadline,

        @Valid
        List<TechnicalImpactRequest> technicalImpacts,

        @Valid
        List<ActionItemRequest> actionItems,

        @Valid
        List<EvidenceRequest> evidences
) {
}
