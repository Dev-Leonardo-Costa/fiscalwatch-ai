package br.com.fiscalwatch.fiscalservice.impactanalysis.dto;

import java.util.List;
import java.util.Map;

public record XsdTypeDefinitionRequest(

        String name,

        String artifact,

        String schemaPath,

        String base,

        List<String> patterns,

        List<String> enumerations,

        Map<String, String> facets
) {
}
