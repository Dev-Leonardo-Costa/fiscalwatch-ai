package br.com.fiscalwatch.fiscalservice.publication.dto;

import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record PublicationHistoryResponse(

        Long id,
        String externalId,
        String source,
        String title,
        DocumentType documentType,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime publishedAt,

        String downloadUrl,
        PublicationDocumentHistoryResponse document

) {
}
