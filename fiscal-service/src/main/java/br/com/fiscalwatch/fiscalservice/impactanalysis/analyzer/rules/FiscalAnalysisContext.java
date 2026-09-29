package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChange;

import java.util.List;

public record FiscalAnalysisContext(

        DocumentContext documentContext,
        List<FiscalChange> fiscalChanges
) {

    public FiscalAnalysisContext {
        fiscalChanges = fiscalChanges == null
                ? List.of()
                : List.copyOf(fiscalChanges);
    }
}
