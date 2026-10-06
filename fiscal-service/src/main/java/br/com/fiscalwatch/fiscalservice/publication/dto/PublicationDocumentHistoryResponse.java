package br.com.fiscalwatch.fiscalservice.publication.dto;

import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record PublicationDocumentHistoryResponse(

        String contentText,
        String contentHash,
        Integer contentLength,
        ExtractionStatus extractionStatus,
        String extractorVersion,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime extractedAt

) {
}
