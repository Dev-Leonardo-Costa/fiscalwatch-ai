package br.com.fiscalwatch.fiscalservice.publication.dto;

import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;

import java.time.LocalDateTime;

public record PublicationResponse(

        Long id,
        String source,
        String title,
        DocumentType documentType,
        LocalDateTime publishedAt,
        LocalDateTime modifiedAt,
        String description,
        String downloadUrl,
        LocalDateTime createdAt

) {
}