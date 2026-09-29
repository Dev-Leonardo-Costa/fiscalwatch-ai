package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RuleBasedImpactAnalyzer implements ImpactAnalyzer {

    private static final int EVIDENCE_CONTEXT_CHARS = 80;
    private static final List<String> HIGH_IMPACT_TERMS = List.of(
            "schema",
            "layout"
    );
    private static final List<String> MEDIUM_IMPACT_TERMS = List.of(
            "prazo",
            "homologacao",
            "homologação"
    );

    @Override
    public ImpactAnalysisResult analyze(PublicationAnalysisInput publication) {

        ImpactLevel impactLevel = resolveImpactLevel(publication);
        String summary = "Analise baseada em regras para a publicacao: "
                + publication.title();
        EvidenceResult evidence = resolveEvidence(publication);

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
                List.of(evidence)
        );
    }

    private ImpactLevel resolveImpactLevel(PublicationAnalysisInput publication) {

        String text = analyzableText(publication)
                .toLowerCase(Locale.ROOT);

        if (containsAny(text, HIGH_IMPACT_TERMS)) {
            return ImpactLevel.HIGH;
        }

        if (containsAny(text, MEDIUM_IMPACT_TERMS)) {
            return ImpactLevel.MEDIUM;
        }

        return ImpactLevel.LOW;
    }

    private EvidenceResult resolveEvidence(PublicationAnalysisInput publication) {

        EvidenceResult documentEvidence = resolveDocumentEvidence(publication);

        if (documentEvidence != null) {
            return documentEvidence;
        }

        return new EvidenceResult(
                publication.title(),
                publication.downloadUrl(),
                null,
                null,
                resolveFallbackEvidenceExcerpt(publication),
                null,
                null
        );
    }

    private EvidenceResult resolveDocumentEvidence(
            PublicationAnalysisInput publication
    ) {

        if (!hasExtractedDocumentText(publication)) {
            return null;
        }

        String contentText = publication.document().contentText();
        RuleOccurrence occurrence = findRuleOccurrence(contentText);

        if (occurrence == null) {
            return null;
        }

        int startPosition = Math.max(
                0,
                occurrence.position() - EVIDENCE_CONTEXT_CHARS
        );
        int endPosition = Math.min(
                contentText.length(),
                occurrence.position()
                        + occurrence.term().length()
                        + EVIDENCE_CONTEXT_CHARS
        );

        return new EvidenceResult(
                publication.title(),
                resolveDocumentSourceUrl(publication),
                null,
                null,
                contentText.substring(startPosition, endPosition),
                startPosition,
                endPosition
        );
    }

    private String analyzableText(PublicationAnalysisInput publication) {

        StringBuilder text = new StringBuilder();

        appendIfNotBlank(text, publication.title());
        appendIfNotBlank(text, publication.description());

        if (hasExtractedDocumentText(publication)) {
            appendIfNotBlank(text, publication.document().contentText());
        }

        return text.toString();
    }

    private boolean hasExtractedDocumentText(
            PublicationAnalysisInput publication
    ) {

        return publication.document() != null
                && publication.document().extractionStatus()
                        == ExtractionStatus.EXTRACTED
                && publication.document().contentText() != null
                && !publication.document().contentText().isBlank();
    }

    private RuleOccurrence findRuleOccurrence(String text) {

        String lowerText = text.toLowerCase(Locale.ROOT);

        RuleOccurrence highImpactOccurrence = findFirstOccurrence(
                lowerText,
                HIGH_IMPACT_TERMS
        );

        if (highImpactOccurrence != null) {
            return highImpactOccurrence;
        }

        return findFirstOccurrence(lowerText, MEDIUM_IMPACT_TERMS);
    }

    private RuleOccurrence findFirstOccurrence(
            String lowerText,
            List<String> terms
    ) {

        RuleOccurrence firstOccurrence = null;

        for (String term : terms) {
            int position = lowerText.indexOf(term);

            if (position < 0) {
                continue;
            }

            if (firstOccurrence == null
                    || position < firstOccurrence.position()) {
                firstOccurrence = new RuleOccurrence(term, position);
            }
        }

        return firstOccurrence;
    }

    private boolean containsAny(String text, List<String> terms) {

        return terms.stream().anyMatch(text::contains);
    }

    private String resolveDocumentSourceUrl(
            PublicationAnalysisInput publication
    ) {

        String sourceUrl = publication.document().sourceUrl();

        if (sourceUrl != null && !sourceUrl.isBlank()) {
            return sourceUrl;
        }

        return publication.downloadUrl();
    }

    private String resolveFallbackEvidenceExcerpt(
            PublicationAnalysisInput publication
    ) {

        if (publication.description() != null
                && !publication.description().isBlank()) {
            return publication.description();
        }

        return publication.title();
    }

    private void appendIfNotBlank(StringBuilder text, String value) {

        if (value == null || value.isBlank()) {
            return;
        }

        if (!text.isEmpty()) {
            text.append(' ');
        }

        text.append(value);
    }

    private record RuleOccurrence(String term, int position) {
    }
}
