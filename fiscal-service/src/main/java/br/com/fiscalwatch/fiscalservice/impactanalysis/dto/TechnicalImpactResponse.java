package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;

import java.time.LocalDateTime;

public record TechnicalImpactResponse(

        Long id,
        String title,
        String description,
        String affectedArea,
        String affectedComponent,
        ImpactLevel impactLevel,
        LocalDateTime createdAt
) {
}
