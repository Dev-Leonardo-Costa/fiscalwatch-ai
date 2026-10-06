package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.SchemaChangeRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class SchemaChangeImpactAnalyzer {

    private static final List<String> SUPPORTED_CHANGE_TYPES = List.of(
            "TYPE_CHANGED",
            "CARDINALITY_CHANGED",
            "ELEMENT_ADDED",
            "ELEMENT_REMOVED",
            "ENUMERATION_ADDED",
            "ENUMERATION_REMOVED"
    );

    public ImpactAnalysisResult analyze(SchemaChangeAnalysisInput input) {

        List<SchemaChangeRequest> supportedChanges = input.changes()
                .stream()
                .filter(change -> SUPPORTED_CHANGE_TYPES.contains(
                        normalizeChangeType(change.changeType())
                ))
                .toList();
        ImpactLevel impactLevel = resolveImpactLevel(supportedChanges);

        return new ImpactAnalysisResult(
                summary(input, supportedChanges),
                impactLevel,
                null,
                null,
                supportedChanges.stream()
                        .map(change -> technicalImpact(change, impactLevel))
                        .toList(),
                supportedChanges.stream()
                        .map(change -> actionItem(change, impactLevel))
                        .toList(),
                supportedChanges.stream()
                        .map(change -> evidence(input, change))
                        .toList()
        );
    }

    private String summary(
            SchemaChangeAnalysisInput input,
            List<SchemaChangeRequest> changes
    ) {

        return "Analise estrutural de schemas XSD da publicacao "
                + input.currentExternalId()
                + " comparando "
                + versionOrFallback(input.previousVersion(), "versao anterior")
                + " com "
                + versionOrFallback(input.currentVersion(), "versao atual")
                + ". Alteracoes suportadas: "
                + changes.size()
                + ".";
    }

    private TechnicalImpactResult technicalImpact(
            SchemaChangeRequest change,
            ImpactLevel impactLevel
    ) {

        String title = switch (normalizeChangeType(change.changeType())) {
            case "TYPE_CHANGED" -> "Tipo de campo XSD alterado";
            case "CARDINALITY_CHANGED" -> "Cardinalidade de campo XSD alterada";
            case "ELEMENT_ADDED" -> "Elemento XSD adicionado";
            case "ELEMENT_REMOVED" -> "Elemento XSD removido";
            case "ENUMERATION_ADDED" -> "Valor permitido adicionado ao XSD";
            case "ENUMERATION_REMOVED" -> "Valor permitido removido do XSD";
            default -> "Alteracao estrutural de schema XSD";
        };

        return new TechnicalImpactResult(
                title,
                "Ajustar componentes que validam, serializam ou consomem "
                        + "o caminho "
                        + affectedPath(change)
                        + " no artefato "
                        + change.artifact()
                        + ".",
                "Schemas fiscais",
                affectedComponent(change),
                impactLevel
        );
    }

    private ActionItemResult actionItem(
            SchemaChangeRequest change,
            ImpactLevel priority
    ) {

        return new ActionItemResult(
                actionTitle(change),
                "Revisar contratos XML, validadores XSD, mapeamentos e testes "
                        + "automatizados relacionados a "
                        + affectedPath(change)
                        + ".",
                "Desenvolvedor fiscal",
                priority,
                null
        );
    }

    private EvidenceResult evidence(
            SchemaChangeAnalysisInput input,
            SchemaChangeRequest change
    ) {

        return new EvidenceResult(
                input.publication().title(),
                input.publication().downloadUrl(),
                change.artifact(),
                null,
                evidenceExcerpt(change),
                null,
                null
        );
    }

    private ImpactLevel resolveImpactLevel(List<SchemaChangeRequest> changes) {

        if (changes.stream().anyMatch(change -> List.of(
                "TYPE_CHANGED",
                "CARDINALITY_CHANGED",
                "ELEMENT_REMOVED",
                "ENUMERATION_REMOVED"
        ).contains(normalizeChangeType(change.changeType())))) {
            return ImpactLevel.HIGH;
        }

        if (!changes.isEmpty()) {
            return ImpactLevel.MEDIUM;
        }

        return ImpactLevel.NONE;
    }

    private String actionTitle(SchemaChangeRequest change) {

        return switch (normalizeChangeType(change.changeType())) {
            case "TYPE_CHANGED" -> "Adequar tipo do campo "
                    + affectedComponent(change);
            case "CARDINALITY_CHANGED" -> "Adequar obrigatoriedade do campo "
                    + affectedComponent(change);
            case "ELEMENT_ADDED" -> "Tratar novo elemento "
                    + affectedComponent(change);
            case "ELEMENT_REMOVED" -> "Remover dependencia do elemento "
                    + affectedComponent(change);
            case "ENUMERATION_ADDED" -> "Aceitar novo valor de dominio "
                    + affectedComponent(change);
            case "ENUMERATION_REMOVED" -> "Bloquear valor removido do dominio "
                    + affectedComponent(change);
            default -> "Avaliar alteracao de schema "
                    + affectedComponent(change);
        };
    }

    private String evidenceExcerpt(SchemaChangeRequest change) {

        return "changeType="
                + normalizeChangeType(change.changeType())
                + "; artifact="
                + change.artifact()
                + "; schemaPath="
                + valueOrFallback(change.schemaPath(), "<sem caminho>")
                + "; symbolName="
                + valueOrFallback(change.symbolName(), "<sem simbolo>")
                + "; before="
                + valueOrFallback(change.before(), "<vazio>")
                + "; after="
                + valueOrFallback(change.after(), "<vazio>");
    }

    private String affectedComponent(SchemaChangeRequest change) {

        return valueOrFallback(change.symbolName(), change.artifact());
    }

    private String affectedPath(SchemaChangeRequest change) {

        return valueOrFallback(change.schemaPath(), affectedComponent(change));
    }

    private String versionOrFallback(String value, String fallback) {

        return valueOrFallback(value, fallback);
    }

    private String valueOrFallback(Object value, String fallback) {

        if (value == null) {
            return fallback;
        }

        String text = String.valueOf(value);

        if (text.isBlank()) {
            return fallback;
        }

        return text;
    }

    private String normalizeChangeType(String changeType) {

        if (changeType == null) {
            return "";
        }

        return changeType.trim().toUpperCase(Locale.ROOT);
    }
}
