package br.com.fiscalwatch.fiscalservice.impactanalysis.exception;

public class ImpactAnalysisNotFoundException extends RuntimeException {

    public ImpactAnalysisNotFoundException(Long id) {
        super("Análise de impacto não encontrada com id: " + id);
    }
}
