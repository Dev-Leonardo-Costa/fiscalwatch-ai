package br.com.fiscalwatch.fiscalservice.publication.messaging;

import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;

import java.time.LocalDateTime;

public record PublicationDocumentEvent(
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
