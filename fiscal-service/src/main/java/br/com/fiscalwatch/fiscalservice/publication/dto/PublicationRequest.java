package br.com.fiscalwatch.fiscalservice.publication.dto;

import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PublicationRequest(

        @NotBlank
        @Size(max = 50)
        String source,

        @NotBlank
        @Size(max = 500)
        String title,

        DocumentType documentType,

        @NotNull
        LocalDateTime publishedAt,

        LocalDateTime modifiedAt,

        String description,

        String downloadUrl

) {
}
