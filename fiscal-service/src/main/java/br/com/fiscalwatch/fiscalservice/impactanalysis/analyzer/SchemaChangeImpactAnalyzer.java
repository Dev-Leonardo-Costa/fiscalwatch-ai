package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.SchemaChangeRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.XsdTypeDefinitionRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

        List<AnalyzedSchemaChange> supportedChanges = input.changes()
                .stream()
                .filter(change -> SUPPORTED_CHANGE_TYPES.contains(
                        normalizeChangeType(change.changeType())
                ))
                .map(this::analyzeChange)
                .toList();
        ImpactLevel impactLevel = resolveImpactLevel(supportedChanges);

        return new ImpactAnalysisResult(
                summary(input, supportedChanges),
                impactLevel,
                null,
                null,
                supportedChanges.stream()
                        .map(this::technicalImpact)
                        .toList(),
                supportedChanges.stream()
                        .map(this::actionItem)
                        .toList(),
                supportedChanges.stream()
                        .map(change -> evidence(input, change))
                        .toList()
        );
    }

    private AnalyzedSchemaChange analyzeChange(SchemaChangeRequest change) {

        if ("TYPE_CHANGED".equals(normalizeChangeType(change.changeType()))) {
            return new AnalyzedSchemaChange(
                    change,
                    classifyTypeChanged(change)
            );
        }

        return new AnalyzedSchemaChange(change, null);
    }

    private String summary(
            SchemaChangeAnalysisInput input,
            List<AnalyzedSchemaChange> changes
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

    private TechnicalImpactResult technicalImpact(AnalyzedSchemaChange analyzed) {

        SchemaChangeRequest change = analyzed.change();

        if (analyzed.typeChangedClassification() != null) {
            TypeChangedClassification classification =
                    analyzed.typeChangedClassification();

            return new TechnicalImpactResult(
                    technicalImpactTitle(classification),
                    typeChangedTechnicalDescription(change, classification),
                    "Schemas fiscais",
                    affectedComponent(change),
                    classification.impactLevel()
            );
        }

        ImpactLevel impactLevel = impactLevelForNonTypeChanged(change);

        String title = switch (normalizeChangeType(change.changeType())) {
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

    private ActionItemResult actionItem(AnalyzedSchemaChange analyzed) {

        SchemaChangeRequest change = analyzed.change();

        if (analyzed.typeChangedClassification() != null) {
            TypeChangedClassification classification =
                    analyzed.typeChangedClassification();

            return new ActionItemResult(
                    typeChangedActionTitle(change, classification),
                    typeChangedActionDescription(change, classification),
                    "Desenvolvedor fiscal",
                    classification.impactLevel(),
                    null
            );
        }

        return new ActionItemResult(
                actionTitle(change),
                "Revisar contratos XML, validadores XSD, mapeamentos e testes "
                        + "automatizados relacionados a "
                        + affectedPath(change)
                        + ".",
                "Desenvolvedor fiscal",
                impactLevelForNonTypeChanged(change),
                null
        );
    }

    private EvidenceResult evidence(
            SchemaChangeAnalysisInput input,
            AnalyzedSchemaChange analyzed
    ) {

        SchemaChangeRequest change = analyzed.change();

        return new EvidenceResult(
                input.publication().title(),
                input.publication().downloadUrl(),
                change.artifact(),
                null,
                evidenceExcerpt(analyzed),
                null,
                null
        );
    }

    private ImpactLevel resolveImpactLevel(List<AnalyzedSchemaChange> changes) {

        if (changes.stream().anyMatch(change ->
                impactLevelForAnalyzedChange(change) == ImpactLevel.HIGH)) {
            return ImpactLevel.HIGH;
        }

        if (changes.stream().anyMatch(change ->
                impactLevelForAnalyzedChange(change) == ImpactLevel.MEDIUM)) {
            return ImpactLevel.MEDIUM;
        }

        return ImpactLevel.NONE;
    }

    private ImpactLevel impactLevelForAnalyzedChange(
            AnalyzedSchemaChange analyzed
    ) {

        if (analyzed.typeChangedClassification() != null) {
            return analyzed.typeChangedClassification().impactLevel();
        }

        return impactLevelForNonTypeChanged(analyzed.change());
    }

    private ImpactLevel impactLevelForNonTypeChanged(
            SchemaChangeRequest change
    ) {

        if (List.of(
                "CARDINALITY_CHANGED",
                "ELEMENT_REMOVED",
                "ENUMERATION_REMOVED"
        ).contains(normalizeChangeType(change.changeType()))) {
            return ImpactLevel.HIGH;
        }

        return ImpactLevel.MEDIUM;
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

    private String evidenceExcerpt(AnalyzedSchemaChange analyzed) {

        SchemaChangeRequest change = analyzed.change();

        if (analyzed.typeChangedClassification() != null) {
            TypeChangedClassification classification =
                    analyzed.typeChangedClassification();

            return commonEvidenceExcerpt(change)
                    + "; classification="
                    + classification.kind()
                    + "; beforeDefinition="
                    + typeDefinitionExcerpt(change.beforeTypeDefinition())
                    + "; afterDefinition="
                    + typeDefinitionExcerpt(change.afterTypeDefinition())
                    + "; differences="
                    + differencesExcerpt(classification);
        }

        return commonEvidenceExcerpt(change);
    }

    private String commonEvidenceExcerpt(SchemaChangeRequest change) {

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

    private TypeChangedClassification classifyTypeChanged(
            SchemaChangeRequest change
    ) {

        XsdTypeDefinitionRequest beforeDefinition =
                change.beforeTypeDefinition();
        XsdTypeDefinitionRequest afterDefinition =
                change.afterTypeDefinition();
        boolean missingBeforeDefinition = beforeDefinition == null;
        boolean missingAfterDefinition = afterDefinition == null;

        if (missingBeforeDefinition || missingAfterDefinition) {
            return new TypeChangedClassification(
                    TypeChangedClassificationKind.MISSING_TYPE_DEFINITION,
                    null,
                    null,
                    List.of(),
                    new EnumerationDiff(List.of(), List.of()),
                    missingBeforeDefinition,
                    missingAfterDefinition,
                    false,
                    ImpactLevel.MEDIUM
            );
        }

        BaseDiff baseDiff = baseDiff(beforeDefinition, afterDefinition);
        PatternDiff patternDiff = patternDiff(
                beforeDefinition,
                afterDefinition
        );
        List<FacetDiff> facetDiffs = facetDiffs(
                beforeDefinition,
                afterDefinition
        );
        EnumerationDiff enumerationDiff = enumerationDiff(
                beforeDefinition,
                afterDefinition
        );
        int changedDimensions = changedDimensions(
                baseDiff,
                patternDiff,
                facetDiffs,
                enumerationDiff
        );
        boolean equivalentDefinition = changedDimensions == 0;

        if (equivalentDefinition) {
            return new TypeChangedClassification(
                    TypeChangedClassificationKind
                            .NOMINAL_ONLY_EQUIVALENT_DEFINITION,
                    baseDiff,
                    patternDiff,
                    facetDiffs,
                    enumerationDiff,
                    false,
                    false,
                    true,
                    ImpactLevel.MEDIUM
            );
        }

        if (changedDimensions > 1) {
            return new TypeChangedClassification(
                    TypeChangedClassificationKind.MIXED_DEFINITION_CHANGED,
                    baseDiff,
                    patternDiff,
                    facetDiffs,
                    enumerationDiff,
                    false,
                    false,
                    false,
                    ImpactLevel.HIGH
            );
        }

        if (baseDiff.changed()) {
            return changedClassification(
                    TypeChangedClassificationKind.BASE_CHANGED,
                    baseDiff,
                    patternDiff,
                    facetDiffs,
                    enumerationDiff,
                    ImpactLevel.HIGH
            );
        }

        if (patternDiff.changed()) {
            return changedClassification(
                    TypeChangedClassificationKind.PATTERN_CHANGED,
                    baseDiff,
                    patternDiff,
                    facetDiffs,
                    enumerationDiff,
                    ImpactLevel.HIGH
            );
        }

        if (!facetDiffs.isEmpty()) {
            return changedClassification(
                    TypeChangedClassificationKind.FACETS_CHANGED,
                    baseDiff,
                    patternDiff,
                    facetDiffs,
                    enumerationDiff,
                    impactLevelForFacetDiffs(facetDiffs)
            );
        }

        return changedClassification(
                TypeChangedClassificationKind.ENUMERATIONS_CHANGED,
                baseDiff,
                patternDiff,
                facetDiffs,
                enumerationDiff,
                enumerationDiff.removed().isEmpty()
                        ? ImpactLevel.MEDIUM
                        : ImpactLevel.HIGH
        );
    }

    private TypeChangedClassification changedClassification(
            TypeChangedClassificationKind kind,
            BaseDiff baseDiff,
            PatternDiff patternDiff,
            List<FacetDiff> facetDiffs,
            EnumerationDiff enumerationDiff,
            ImpactLevel impactLevel
    ) {

        return new TypeChangedClassification(
                kind,
                baseDiff,
                patternDiff,
                facetDiffs,
                enumerationDiff,
                false,
                false,
                false,
                impactLevel
        );
    }

    private BaseDiff baseDiff(
            XsdTypeDefinitionRequest beforeDefinition,
            XsdTypeDefinitionRequest afterDefinition
    ) {

        String before = normalizedValue(beforeDefinition.base());
        String after = normalizedValue(afterDefinition.base());

        return new BaseDiff(before, after, !before.equals(after));
    }

    private PatternDiff patternDiff(
            XsdTypeDefinitionRequest beforeDefinition,
            XsdTypeDefinitionRequest afterDefinition
    ) {

        List<String> before = normalizedList(beforeDefinition.patterns());
        List<String> after = normalizedList(afterDefinition.patterns());

        return new PatternDiff(before, after, !before.equals(after));
    }

    private List<FacetDiff> facetDiffs(
            XsdTypeDefinitionRequest beforeDefinition,
            XsdTypeDefinitionRequest afterDefinition
    ) {

        Map<String, String> before = normalizedMap(beforeDefinition.facets());
        Map<String, String> after = normalizedMap(afterDefinition.facets());
        Set<String> keys = new TreeSet<>();
        List<FacetDiff> diffs = new ArrayList<>();

        keys.addAll(before.keySet());
        keys.addAll(after.keySet());

        for (String key : keys) {
            String beforeValue = before.get(key);
            String afterValue = after.get(key);

            if (!Objects.equals(beforeValue, afterValue)) {
                diffs.add(new FacetDiff(key, beforeValue, afterValue));
            }
        }

        return diffs;
    }

    private EnumerationDiff enumerationDiff(
            XsdTypeDefinitionRequest beforeDefinition,
            XsdTypeDefinitionRequest afterDefinition
    ) {

        Set<String> before = new TreeSet<>(
                normalizedList(beforeDefinition.enumerations())
        );
        Set<String> after = new TreeSet<>(
                normalizedList(afterDefinition.enumerations())
        );
        List<String> added = after.stream()
                .filter(value -> !before.contains(value))
                .toList();
        List<String> removed = before.stream()
                .filter(value -> !after.contains(value))
                .toList();

        return new EnumerationDiff(added, removed);
    }

    private int changedDimensions(
            BaseDiff baseDiff,
            PatternDiff patternDiff,
            List<FacetDiff> facetDiffs,
            EnumerationDiff enumerationDiff
    ) {

        int count = 0;

        if (baseDiff.changed()) {
            count++;
        }

        if (patternDiff.changed()) {
            count++;
        }

        if (!facetDiffs.isEmpty()) {
            count++;
        }

        if (enumerationDiff.changed()) {
            count++;
        }

        return count;
    }

    private ImpactLevel impactLevelForFacetDiffs(List<FacetDiff> facetDiffs) {

        List<String> highImpactFacets = List.of(
                "fractionDigits",
                "totalDigits",
                "minInclusive",
                "maxInclusive",
                "minExclusive",
                "maxExclusive"
        );

        if (facetDiffs.stream().anyMatch(diff ->
                highImpactFacets.contains(diff.name()))) {
            return ImpactLevel.HIGH;
        }

        return ImpactLevel.MEDIUM;
    }

    private String technicalImpactTitle(
            TypeChangedClassification classification
    ) {

        return switch (classification.kind()) {
            case MISSING_TYPE_DEFINITION ->
                    "Tipo XSD alterado sem definição completa disponível";
            case NOMINAL_ONLY_EQUIVALENT_DEFINITION ->
                    "Tipo XSD referenciado alterado sem mudança aparente de restrição";
            case BASE_CHANGED -> "Base do tipo XSD alterada";
            case PATTERN_CHANGED -> "Formato aceito pelo tipo XSD alterado";
            case FACETS_CHANGED -> "Restrições do tipo XSD alteradas";
            case ENUMERATIONS_CHANGED -> "Domínio permitido pelo tipo XSD alterado";
            case MIXED_DEFINITION_CHANGED ->
                    "Definição do tipo XSD alterada em múltiplas restrições";
        };
    }

    private String typeChangedTechnicalDescription(
            SchemaChangeRequest change,
            TypeChangedClassification classification
    ) {

        return switch (classification.kind()) {
            case MISSING_TYPE_DEFINITION -> "O campo "
                    + affectedComponent(change)
                    + " mudou de "
                    + valueOrFallback(change.before(), "<vazio>")
                    + " para "
                    + valueOrFallback(change.after(), "<vazio>")
                    + " em "
                    + affectedPath(change)
                    + ", mas uma ou ambas as definições dos tipos não foram "
                    + "localizadas nos artefatos comparados. "
                    + differencesSentence(classification);
            case NOMINAL_ONLY_EQUIVALENT_DEFINITION -> "O campo "
                    + affectedComponent(change)
                    + " mudou de "
                    + valueOrFallback(change.before(), "<vazio>")
                    + " para "
                    + valueOrFallback(change.after(), "<vazio>")
                    + " em "
                    + affectedPath(change)
                    + ", mas as definições resolvidas possuem base, patterns, "
                    + "enumerações e facets equivalentes.";
            case BASE_CHANGED -> "O campo "
                    + affectedComponent(change)
                    + " mudou de "
                    + valueOrFallback(change.before(), "<vazio>")
                    + " para "
                    + valueOrFallback(change.after(), "<vazio>")
                    + " em "
                    + affectedPath(change)
                    + "; "
                    + baseDiffSentence(classification.baseDiff())
                    + ".";
            case PATTERN_CHANGED -> "O campo "
                    + affectedComponent(change)
                    + " mudou de "
                    + valueOrFallback(change.before(), "<vazio>")
                    + " para "
                    + valueOrFallback(change.after(), "<vazio>")
                    + " em "
                    + affectedPath(change)
                    + "; "
                    + patternDiffSentence(classification.patternDiff())
                    + ".";
            case FACETS_CHANGED -> "O campo "
                    + affectedComponent(change)
                    + " mudou de "
                    + valueOrFallback(change.before(), "<vazio>")
                    + " para "
                    + valueOrFallback(change.after(), "<vazio>")
                    + " em "
                    + affectedPath(change)
                    + "; facets alteradas: "
                    + facetDiffsExcerpt(classification.facetDiffs())
                    + ".";
            case ENUMERATIONS_CHANGED -> "O campo "
                    + affectedComponent(change)
                    + " mudou de "
                    + valueOrFallback(change.before(), "<vazio>")
                    + " para "
                    + valueOrFallback(change.after(), "<vazio>")
                    + " em "
                    + affectedPath(change)
                    + "; enumerações alteradas: "
                    + enumerationDiffExcerpt(classification.enumerationDiff())
                    + ".";
            case MIXED_DEFINITION_CHANGED -> "O campo "
                    + affectedComponent(change)
                    + " mudou de "
                    + valueOrFallback(change.before(), "<vazio>")
                    + " para "
                    + valueOrFallback(change.after(), "<vazio>")
                    + " em "
                    + affectedPath(change)
                    + "; múltiplas diferenças foram encontradas: "
                    + differencesExcerpt(classification)
                    + ".";
        };
    }

    private String typeChangedActionTitle(
            SchemaChangeRequest change,
            TypeChangedClassification classification
    ) {

        return switch (classification.kind()) {
            case MISSING_TYPE_DEFINITION -> "Revisar manualmente os tipos "
                    + valueOrFallback(change.before(), "<vazio>")
                    + " e "
                    + valueOrFallback(change.after(), "<vazio>");
            case NOMINAL_ONLY_EQUIVALENT_DEFINITION ->
                    "Verificar compatibilidade do novo tipo "
                            + valueOrFallback(change.after(), "<vazio>");
            case BASE_CHANGED ->
                    "Adequar modelo e serialização para nova base XSD";
            case PATTERN_CHANGED -> "Atualizar validações de formato de "
                    + affectedComponent(change);
            case FACETS_CHANGED ->
                    "Atualizar restrições de validação de "
                            + affectedComponent(change);
            case ENUMERATIONS_CHANGED -> "Atualizar domínio permitido de "
                    + affectedComponent(change);
            case MIXED_DEFINITION_CHANGED ->
                    "Revisar definição completa do tipo "
                            + valueOrFallback(change.after(), "<vazio>");
        };
    }

    private String typeChangedActionDescription(
            SchemaChangeRequest change,
            TypeChangedClassification classification
    ) {

        return "Revisar "
                + affectedComponent(change)
                + " em "
                + affectedPath(change)
                + ", considerando a mudança de "
                + valueOrFallback(change.before(), "<vazio>")
                + " para "
                + valueOrFallback(change.after(), "<vazio>")
                + ". Diferenças encontradas: "
                + differencesExcerpt(classification)
                + ".";
    }

    private String differencesSentence(
            TypeChangedClassification classification
    ) {

        if (classification.missingBeforeDefinition()
                && classification.missingAfterDefinition()) {
            return "As definições anterior e posterior não foram localizadas.";
        }

        if (classification.missingBeforeDefinition()) {
            return "A definição anterior não foi localizada.";
        }

        if (classification.missingAfterDefinition()) {
            return "A definição posterior não foi localizada.";
        }

        return differencesExcerpt(classification);
    }

    private String differencesExcerpt(
            TypeChangedClassification classification
    ) {

        List<String> parts = new ArrayList<>();

        if (classification.equivalentDefinition()) {
            parts.add("definições efetivas equivalentes");
        }

        if (classification.missingBeforeDefinition()) {
            parts.add("beforeDefinition=<ausente>");
        }

        if (classification.missingAfterDefinition()) {
            parts.add("afterDefinition=<ausente>");
        }

        if (classification.baseDiff() != null
                && classification.baseDiff().changed()) {
            parts.add(baseDiffSentence(classification.baseDiff()));
        }

        if (classification.patternDiff() != null
                && classification.patternDiff().changed()) {
            parts.add(patternDiffSentence(classification.patternDiff()));
        }

        if (!classification.facetDiffs().isEmpty()) {
            parts.add("facetDiffs="
                    + facetDiffsExcerpt(classification.facetDiffs()));
        }

        if (classification.enumerationDiff().changed()) {
            parts.add("enumerationDiff="
                    + enumerationDiffExcerpt(
                            classification.enumerationDiff()
                    ));
        }

        if (parts.isEmpty()) {
            return "<sem diferenças efetivas>";
        }

        return String.join("; ", parts);
    }

    private String baseDiffSentence(BaseDiff diff) {

        return "base: "
                + valueOrFallback(diff.before(), "<vazio>")
                + " -> "
                + valueOrFallback(diff.after(), "<vazio>");
    }

    private String patternDiffSentence(PatternDiff diff) {

        return "patterns: "
                + diff.before()
                + " -> "
                + diff.after();
    }

    private String facetDiffsExcerpt(List<FacetDiff> diffs) {

        return diffs.stream()
                .map(diff -> diff.name()
                        + ": "
                        + valueOrFallback(diff.before(), "<vazio>")
                        + " -> "
                        + valueOrFallback(diff.after(), "<vazio>"))
                .toList()
                .toString();
    }

    private String enumerationDiffExcerpt(EnumerationDiff diff) {

        return "adicionadas="
                + diff.added()
                + ", removidas="
                + diff.removed();
    }

    private String typeDefinitionExcerpt(
            XsdTypeDefinitionRequest definition
    ) {

        if (definition == null) {
            return "<ausente>";
        }

        return "{name="
                + valueOrFallback(definition.name(), "<sem nome>")
                + ", artifact="
                + valueOrFallback(definition.artifact(), "<sem artefato>")
                + ", schemaPath="
                + valueOrFallback(definition.schemaPath(), "<sem caminho>")
                + ", base="
                + valueOrFallback(definition.base(), "<sem base>")
                + ", patterns="
                + safeList(definition.patterns())
                + ", enumerations="
                + safeList(definition.enumerations())
                + ", facets="
                + normalizedMap(definition.facets())
                + "}";
    }

    private List<String> normalizedList(List<String> values) {

        if (values == null) {
            return List.of();
        }

        return values.stream()
                .map(this::normalizedValue)
                .toList();
    }

    private Map<String, String> normalizedMap(Map<String, String> values) {

        if (values == null) {
            return Map.of();
        }

        Map<String, String> normalized = new TreeMap<>();

        values.forEach((key, value) -> normalized.put(
                normalizedValue(key),
                normalizedValue(value)
        ));

        return normalized;
    }

    private List<String> safeList(List<String> values) {

        if (values == null) {
            return List.of();
        }

        return values;
    }

    private String normalizedValue(String value) {

        if (value == null) {
            return "";
        }

        return value.trim();
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

    private record AnalyzedSchemaChange(
            SchemaChangeRequest change,
            TypeChangedClassification typeChangedClassification
    ) {
    }

    private enum TypeChangedClassificationKind {
        MISSING_TYPE_DEFINITION,
        NOMINAL_ONLY_EQUIVALENT_DEFINITION,
        BASE_CHANGED,
        PATTERN_CHANGED,
        FACETS_CHANGED,
        ENUMERATIONS_CHANGED,
        MIXED_DEFINITION_CHANGED
    }

    private record TypeChangedClassification(
            TypeChangedClassificationKind kind,
            BaseDiff baseDiff,
            PatternDiff patternDiff,
            List<FacetDiff> facetDiffs,
            EnumerationDiff enumerationDiff,
            boolean missingBeforeDefinition,
            boolean missingAfterDefinition,
            boolean equivalentDefinition,
            ImpactLevel impactLevel
    ) {
    }

    private record BaseDiff(
            String before,
            String after,
            boolean changed
    ) {
    }

    private record PatternDiff(
            List<String> before,
            List<String> after,
            boolean changed
    ) {
    }

    private record FacetDiff(
            String name,
            String before,
            String after
    ) {
    }

    private record EnumerationDiff(
            List<String> added,
            List<String> removed
    ) {

        boolean changed() {
            return !added.isEmpty() || !removed.isEmpty();
        }
    }
}
