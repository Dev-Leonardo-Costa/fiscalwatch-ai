package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;

import java.time.LocalDateTime;

public record PublicationDocumentAnalysisInput(

        String sourceUrl,
        String contentText,
        String contentHash,
        Integer contentLength,
        ExtractionStatus extractionStatus,
        String extractionError,
        String extractorVersion,
        LocalDateTime extractedAt
) {
}
