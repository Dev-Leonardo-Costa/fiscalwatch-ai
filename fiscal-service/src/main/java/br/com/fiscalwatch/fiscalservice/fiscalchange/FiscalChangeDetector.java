package br.com.fiscalwatch.fiscalservice.fiscalchange;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.DocumentContext;

import java.util.List;

public interface FiscalChangeDetector {

    List<FiscalChange> detect(DocumentContext context);
}
