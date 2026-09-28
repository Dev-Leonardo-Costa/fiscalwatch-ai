package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

public record EvidenceResult(

        String sourceTitle,
        String sourceUrl,
        String documentSection,
        Integer pageNumber,
        String excerpt,
        Integer startPosition,
        Integer endPosition
) {
}
