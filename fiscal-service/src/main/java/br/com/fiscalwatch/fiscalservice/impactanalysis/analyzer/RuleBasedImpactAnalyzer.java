package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.CfopValidationRule;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.DocumentContext;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.EnvironmentDeadlineRule;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.FiscalRuleEngine;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.NegationDetector;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.RuleMatch;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Component
public class RuleBasedImpactAnalyzer implements ImpactAnalyzer {

    private static final int EVIDENCE_CONTEXT_CHARS = 80;
    private static final List<String> HIGH_IMPACT_TERMS = List.of(
            "schema",
            "layout",
            "leiaute"
    );
    private static final List<String> MEDIUM_IMPACT_TERMS = List.of(
            "prazo",
            "homologacao",
            "homologação"
    );

    private final FiscalRuleEngine fiscalRuleEngine;
    private final NegationDetector negationDetector;

    public RuleBasedImpactAnalyzer() {
        this(new FiscalRuleEngine(List.of(
                new CfopValidationRule(),
                new EnvironmentDeadlineRule()
        )));
    }

    RuleBasedImpactAnalyzer(FiscalRuleEngine fiscalRuleEngine) {
        this.fiscalRuleEngine = fiscalRuleEngine;
        this.negationDetector = new NegationDetector();
    }

    @Override
    public ImpactAnalysisResult analyze(PublicationAnalysisInput publication) {

        DocumentContext context = DocumentContext.from(publication);
        List<RuleMatch> matches = fiscalRuleEngine.evaluate(context);
        ImpactLevel impactLevel = resolveImpactLevel(context);
        String summary = "Analise baseada em regras para a publicacao: "
                + publication.title();
        LocalDateTime homologationDeadline = firstHomologationDeadline(
                matches
        );
        LocalDateTime productionDeadline = firstProductionDeadline(matches);
        List<RuleMatch> contentMatches = matches.stream()
                .filter(RuleMatch::hasImpactContent)
                .toList();

        if (contentMatches.isEmpty()) {
            return fallbackResult(
                    publication,
                    context,
                    impactLevel,
                    summary,
                    homologationDeadline,
                    productionDeadline
            );
        }

        return new ImpactAnalysisResult(
                summary,
                impactLevel,
                homologationDeadline,
                productionDeadline,
                contentMatches.stream()
                        .flatMap(match -> match.technicalImpacts().stream())
                        .map(technicalImpact -> withImpactLevel(
                                technicalImpact,
                                impactLevel
                        ))
                        .toList(),
                contentMatches.stream()
                        .flatMap(match -> match.actionItems().stream())
                        .map(actionItem -> withPriority(
                                actionItem,
                                impactLevel
                        ))
                        .toList(),
                contentMatches.stream()
                        .flatMap(match -> match.evidences().stream())
                        .toList()
        );
    }

    private ImpactAnalysisResult fallbackResult(
            PublicationAnalysisInput publication,
            DocumentContext context,
            ImpactLevel impactLevel,
            String summary,
            LocalDateTime homologationDeadline,
            LocalDateTime productionDeadline
    ) {

        return new ImpactAnalysisResult(
                summary,
                impactLevel,
                homologationDeadline,
                productionDeadline,
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
                List.of(resolveFallbackEvidence(publication, context))
        );
    }

    private ImpactLevel resolveImpactLevel(DocumentContext context) {

        String text = context.analyzableText();
        String lowerText = text.toLowerCase(Locale.ROOT);

        if (containsNonNegatedTerm(text, lowerText, HIGH_IMPACT_TERMS)) {
            return ImpactLevel.HIGH;
        }

        if (containsAny(lowerText, MEDIUM_IMPACT_TERMS)) {
            return ImpactLevel.MEDIUM;
        }

        return ImpactLevel.LOW;
    }

    private EvidenceResult resolveFallbackEvidence(
            PublicationAnalysisInput publication,
            DocumentContext context
    ) {

        EvidenceResult documentEvidence = resolveLegacyDocumentEvidence(
                publication,
                context
        );

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

    private EvidenceResult resolveLegacyDocumentEvidence(
            PublicationAnalysisInput publication,
            DocumentContext context
    ) {

        if (!context.documentExtracted()) {
            return null;
        }

        String contentText = context.documentContentText();
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
                context.documentSourceUrlOrFallback(),
                null,
                null,
                contentText.substring(startPosition, endPosition),
                startPosition,
                endPosition
        );
    }

    private RuleOccurrence findRuleOccurrence(String text) {

        String lowerText = text.toLowerCase(Locale.ROOT);

        RuleOccurrence highImpactOccurrence = findFirstNonNegatedOccurrence(
                text,
                lowerText,
                HIGH_IMPACT_TERMS
        );

        if (highImpactOccurrence != null) {
            return highImpactOccurrence;
        }

        return findFirstOccurrence(lowerText, MEDIUM_IMPACT_TERMS);
    }

    private RuleOccurrence findFirstNonNegatedOccurrence(
            String originalText,
            String lowerText,
            List<String> terms
    ) {

        RuleOccurrence firstOccurrence = null;

        for (String term : terms) {
            int position = lowerText.indexOf(term);

            if (position < 0
                    || negationDetector.isNegatedAt(
                            originalText,
                            position
                    )) {
                continue;
            }

            if (firstOccurrence == null
                    || position < firstOccurrence.position()) {
                firstOccurrence = new RuleOccurrence(term, position);
            }
        }

        return firstOccurrence;
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

    private boolean containsNonNegatedTerm(
            String originalText,
            String lowerText,
            List<String> terms
    ) {

        return terms.stream().anyMatch(term -> {
            int position = lowerText.indexOf(term);

            return position >= 0
                    && !negationDetector.isNegatedAt(originalText, position);
        });
    }

    private boolean containsAny(String text, List<String> terms) {

        return terms.stream().anyMatch(text::contains);
    }

    private LocalDateTime firstHomologationDeadline(List<RuleMatch> matches) {
        return matches.stream()
                .map(RuleMatch::homologationDeadline)
                .filter(deadline -> deadline != null)
                .findFirst()
                .orElse(null);
    }

    private LocalDateTime firstProductionDeadline(List<RuleMatch> matches) {
        return matches.stream()
                .map(RuleMatch::productionDeadline)
                .filter(deadline -> deadline != null)
                .findFirst()
                .orElse(null);
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

    private TechnicalImpactResult withImpactLevel(
            TechnicalImpactResult technicalImpact,
            ImpactLevel impactLevel
    ) {

        return new TechnicalImpactResult(
                technicalImpact.title(),
                technicalImpact.description(),
                technicalImpact.affectedArea(),
                technicalImpact.affectedComponent(),
                impactLevel
        );
    }

    private ActionItemResult withPriority(
            ActionItemResult actionItem,
            ImpactLevel priority
    ) {

        return new ActionItemResult(
                actionItem.title(),
                actionItem.description(),
                actionItem.targetProfessionalProfile(),
                priority,
                actionItem.dueAt()
        );
    }

    private record RuleOccurrence(String term, int position) {
    }
}
