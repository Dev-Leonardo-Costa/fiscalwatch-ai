package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;

public record TechnicalImpactResult(

        String title,
        String description,
        String affectedArea,
        String affectedComponent,
        ImpactLevel impactLevel
) {
}
