package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;

import java.time.LocalDateTime;

public record PublicationAnalysisInput(

        Long id,
        String externalId,
        String source,
        String title,
        DocumentType documentType,
        LocalDateTime publishedAt,
        LocalDateTime modifiedAt,
        String description,
        String downloadUrl
) {
}
