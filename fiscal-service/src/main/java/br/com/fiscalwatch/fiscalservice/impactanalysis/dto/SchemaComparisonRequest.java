package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SchemaComparisonRequest(

        @NotBlank
        @Size(max = 64)
        String currentExternalId,

        @Size(max = 64)
        String previousExternalId,

        String currentVersion,

        String previousVersion,

        @NotNull
        @Valid
        List<SchemaChangeRequest> changes
) {
}
