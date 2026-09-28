package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ActionItemRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        String description,

        @NotBlank
        @Size(max = 100)
        String targetProfessionalProfile,

        @NotNull
        ImpactLevel priority,

        LocalDateTime dueAt
) {
}
