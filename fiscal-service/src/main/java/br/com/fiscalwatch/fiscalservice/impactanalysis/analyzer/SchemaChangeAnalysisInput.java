package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.SchemaChangeRequest;

import java.util.List;

public record SchemaChangeAnalysisInput(

        PublicationAnalysisInput publication,
        String currentExternalId,
        String previousExternalId,
        String currentVersion,
        String previousVersion,
        List<SchemaChangeRequest> changes
) {
}
