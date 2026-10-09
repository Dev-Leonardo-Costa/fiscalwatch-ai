package br.com.fiscalwatch.fiscalservice.impactanalysis.job;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.ImpactAnalysisService;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.PublicationDocumentValidator;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicBoolean;

public class SvrsImpactAnalysisJob {
    private static final Logger log = LoggerFactory.getLogger(SvrsImpactAnalysisJob.class);
    private final PublicationDocumentRepository documents;
    private final ImpactAnalysisService analyses;
    private final SvrsImpactAnalysisJobProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();
    private long afterPublicationId;

    public SvrsImpactAnalysisJob(PublicationDocumentRepository documents, ImpactAnalysisService analyses,
                                 SvrsImpactAnalysisJobProperties properties) {
        this.documents = documents;
        this.analyses = analyses;
        this.properties = properties;
    }

    /**
     * Uma consulta/lote por execução; não repete falhas neste ciclo.
     * Cursor em memória dá oportunidade aos IDs seguintes e volta ao início
     * ao esgotar a consulta. Reinício começa no zero e retoma pendências do banco.
     * O guard é local; entre instâncias a unicidade depende do lock do serviço.
     */
    @Scheduled(fixedDelayString = "${fiscalwatch.impact-analysis-job.fixed-delay-ms:300000}",
            initialDelayString = "${fiscalwatch.impact-analysis-job.initial-delay-ms:300000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runOnce() {
        if (!properties.enabled() || !running.compareAndSet(false, true)) return;
        try {
            var batch = documents.findPendingSvrsAnalysisDocuments(afterPublicationId,
                    ExtractionStatus.EXTRACTED, "automatic-v1", AnalysisStatus.COMPLETED,
                    PageRequest.of(0, properties.batchSize()));
            if (batch.isEmpty()) {
                afterPublicationId = 0;
                return;
            }
            for (var document : batch) {
                Long publicationId = document.getPublication().getId();
                afterPublicationId = publicationId;
                try {
                    PublicationDocumentValidator.validate(document);
                    // O proxy do serviço só retorna após commit; cada item tem sua transação.
                    var result = analyses.analyzePublication(publicationId);
                    log.info("Análise SVRS disponível. publicationId={}, analysisId={}",
                            publicationId, result.id());
                } catch (RuntimeException exception) {
                    // Não registra mensagem/stacktrace que possam conter SQL, texto ou credenciais.
                    log.warn("Análise SVRS não concluída. publicationId={}, tipoErro={}",
                            publicationId, exception.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Consulta de pendências SVRS falhou. tipoErro={}",
                    exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
