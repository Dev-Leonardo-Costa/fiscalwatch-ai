package br.com.fiscalwatch.fiscalservice.fiscalchange;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;

import java.util.List;

public record FiscalChange(

        FiscalChangeType changeType,
        String ruleCode,
        String affectedElement,
        String affectedDocument,
        List<String> referencedValues,
        String description,
        EvidenceResult evidence
) {

    public FiscalChange {
        referencedValues = referencedValues == null
                ? List.of()
                : List.copyOf(referencedValues);
    }
}
