package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuleBasedImpactAnalyzer implements ImpactAnalyzer {

    @Override
    public ImpactAnalysisResult analyze(PublicationAnalysisInput publication) {

        ImpactLevel impactLevel = resolveImpactLevel(publication);
        String summary = "Analise baseada em regras para a publicacao: "
                + publication.title();

        return new ImpactAnalysisResult(
                summary,
                impactLevel,
                null,
                null,
                List.of(new TechnicalImpactResult(
                        "Revisar impacto tecnico da publicacao",
                        "A publicacao deve ser revisada para identificar "
                                + "alteracoes tecnicas aplicaveis.",
                        "Fiscal",
                        publication.documentType() == null
                                ? "Documento fiscal"
                                : publication.documentType().name(),
                        impactLevel
                )),
                List.of(new ActionItemResult(
                        "Avaliar publicacao fiscal",
                        "Validar manualmente os impactos antes de definir "
                                + "plano de implementacao.",
                        "Analista fiscal",
                        impactLevel,
                        null
                )),
                List.of(new EvidenceResult(
                        publication.title(),
                        publication.downloadUrl(),
                        null,
                        null,
                        resolveEvidenceExcerpt(publication),
                        null,
                        null
                ))
        );
    }

    private ImpactLevel resolveImpactLevel(PublicationAnalysisInput publication) {

        String text = (
                String.valueOf(publication.title())
                        + " "
                        + String.valueOf(publication.description())
        ).toLowerCase();

        if (text.contains("schema") || text.contains("layout")) {
            return ImpactLevel.HIGH;
        }

        if (text.contains("prazo") || text.contains("homologacao")) {
            return ImpactLevel.MEDIUM;
        }

        return ImpactLevel.LOW;
    }

    private String resolveEvidenceExcerpt(PublicationAnalysisInput publication) {

        if (publication.description() != null
                && !publication.description().isBlank()) {
            return publication.description();
        }

        return publication.title();
    }
}
