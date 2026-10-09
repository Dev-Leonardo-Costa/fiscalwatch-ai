package br.com.fiscalwatch.fiscalservice.impactanalysis.job;

import br.com.fiscalwatch.fiscalservice.impactanalysis.service.ImpactAnalysisService;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "fiscalwatch.impact-analysis-job", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SvrsImpactAnalysisJobProperties.class)
@EnableScheduling
public class SvrsImpactAnalysisJobConfiguration {
    @Bean
    SvrsImpactAnalysisJob svrsImpactAnalysisJob(PublicationDocumentRepository documents,
                                               ImpactAnalysisService analyses,
                                               SvrsImpactAnalysisJobProperties properties) {
        return new SvrsImpactAnalysisJob(documents, analyses, properties);
    }
}
