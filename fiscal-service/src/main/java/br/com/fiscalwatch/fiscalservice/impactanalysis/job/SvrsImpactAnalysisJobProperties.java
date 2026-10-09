package br.com.fiscalwatch.fiscalservice.impactanalysis.job;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiscalwatch.impact-analysis-job")
public record SvrsImpactAnalysisJobProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("300000") long fixedDelayMs,
        @DefaultValue("300000") long initialDelayMs,
        @DefaultValue("20") int batchSize) {
    public SvrsImpactAnalysisJobProperties {
        if (fixedDelayMs < 1000 || initialDelayMs < 0 || batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Configuração inválida do job de análise SVRS");
        }
    }
}
