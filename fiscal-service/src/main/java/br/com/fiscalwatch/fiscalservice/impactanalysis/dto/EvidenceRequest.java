package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EvidenceRequest(

        @Size(max = 500)
        String sourceTitle,

        String sourceUrl,

        @Size(max = 200)
        String documentSection,

        Integer pageNumber,

        @NotBlank
        String excerpt,

        Integer startPosition,

        Integer endPosition
) {
}
