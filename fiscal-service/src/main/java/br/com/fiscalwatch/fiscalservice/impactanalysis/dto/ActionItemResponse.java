package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;

import java.time.LocalDateTime;

public record ActionItemResponse(

        Long id,
        String title,
        String description,
        String targetProfessionalProfile,
        ImpactLevel priority,
        LocalDateTime dueAt,
        Boolean completed,
        LocalDateTime createdAt
) {
}
