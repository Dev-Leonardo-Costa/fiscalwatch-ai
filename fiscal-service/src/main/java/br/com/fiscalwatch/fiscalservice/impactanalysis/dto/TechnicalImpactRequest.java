package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TechnicalImpactRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        String description,

        @Size(max = 100)
        String affectedArea,

        @Size(max = 150)
        String affectedComponent,

        @NotNull
        ImpactLevel impactLevel
) {
}
