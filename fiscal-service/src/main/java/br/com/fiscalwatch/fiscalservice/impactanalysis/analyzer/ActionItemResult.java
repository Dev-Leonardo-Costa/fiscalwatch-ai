package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;

import java.time.LocalDateTime;

public record ActionItemResult(

        String title,
        String description,
        String targetProfessionalProfile,
        ImpactLevel priority,
        LocalDateTime dueAt
) {
}
