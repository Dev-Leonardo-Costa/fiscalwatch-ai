package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import jakarta.validation.constraints.NotBlank;

public record SchemaChangeRequest(

        @NotBlank
        String artifact,

        @NotBlank
        String changeType,

        String schemaPath,

        String symbolName,

        Object before,

        Object after
) {
}
