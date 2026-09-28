package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import java.time.LocalDateTime;

public record EvidenceResponse(

        Long id,
        String sourceTitle,
        String sourceUrl,
        String documentSection,
        Integer pageNumber,
        String excerpt,
        Integer startPosition,
        Integer endPosition,
        LocalDateTime createdAt
) {
}
