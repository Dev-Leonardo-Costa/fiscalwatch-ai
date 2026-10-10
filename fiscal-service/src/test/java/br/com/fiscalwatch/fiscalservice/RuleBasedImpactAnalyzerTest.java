package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChange;
import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChangeDetector;
import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChangeType;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ActionItemResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.TechnicalImpactResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalysisResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationDocumentAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.RuleBasedImpactAnalyzer;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.FiscalAnalysisContext;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.FiscalRuleEngine;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.RuleMatch;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedImpactAnalyzerTest {

    private RuleBasedImpactAnalyzer analyzer;

    @BeforeEach
    void configurar() {
        analyzer = new RuleBasedImpactAnalyzer();
    }

    @Test
    void naoDeveGerarImpactosOuAcoesCfopParaValidacaoGtin() {
        String contentText = "Nota Técnica 2021.003\nValidação GTIN\n"
                + "Versão 1.21 – maio de 2023\n\n"
                + "3 Leiaute da Nota Fiscal Eletrônica\n"
                + "Anexo II – CFOP para validação do GTIN\n\n"
                + "Mantida a verificação da existência do GTIN no CCG somente "
                + "para as operações de venda da Indústria "
                + "(CFOP de Venda Produção do Estabelecimento);";
        ImpactAnalysisResult result = analyzer.analyze(criarInput(
                "Publicada a versão 1.21 da NT 2021.003 da NF-e, que trata da validação do GTIN.",
                null, criarDocumento(ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf", contentText)));

        assertTrue(result.technicalImpacts().stream().noneMatch(impact ->
                impact.title().contains("CFOP") || impact.description().contains("CFOP")));
        assertTrue(result.actionItems().stream().noneMatch(action ->
                action.title().contains("CFOP") || action.description().contains("CFOP")));
        assertNotEquals(ImpactLevel.HIGH, result.impactLevel());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Sumário\n3 Leiaute da Nota Fiscal Eletrônica ................ 8",
            "Sumário\n3 Alteração do leiaute da NF-e ................ 8",
            "Referência informativa ao schema XML e ao leiaute da NF-e.",
            "Referência informativa sobre alteração do leiaute da NF-e.",
            "Exemplo hipotético de alteração do schema XML.",
            "Se esta NT altera o leiaute, o ERP precisa de ajustes.",
            "O leiaute da NF-e não sofreu alteração.",
            "O schema XML permanece inalterado.",
            "O layout da NF-e foi copiado do MOC sem alterações.",
            "Ausência de alterações no leiaute da NF-e.",
            "Nenhuma alteração no leiaute da NF-e.",
            "Sem modificações no schema XML.",
            "O schema XSD foi alterado? Não, permanece inalterado.",
            "Esta NT não altera o leiaute da NF-e nem os schemas XML.",
            "Esta NT altera o leiaute? Não, as definições foram copiadas sem alterações."
    })
    void naoDeveGerarHighSemMudancaEstruturalAfirmada(String contentText) {
        assertNotEquals(ImpactLevel.HIGH, analisarTexto(contentText).impactLevel());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Esta NT altera o leiaute da NF-e.",
            "O documento oficial modifica o layout da NF-e.",
            "Alteração do schema XSD da NF-e.",
            "O schema XSD da NF-e será alterado.",
            "O leiaute não sofreu alteração. Esta NT altera o schema XSD.",
            "Esta NT não altera o schema XML. Esta NT altera o schema XML.",
            "O schema não será alterado, mas esta NT modifica o leiaute da NF-e."
    })
    void devePreservarHighParaMudancaEstruturalComEvidence(String contentText) {
        ImpactAnalysisResult result = analisarTexto(contentText);
        assertEquals(ImpactLevel.HIGH, result.impactLevel());
        assertTrue(result.technicalImpacts().stream().anyMatch(i -> i.impactLevel() == ImpactLevel.HIGH));
        assertTrue(result.evidences().stream().anyMatch(e -> e.excerpt().contains("altera")
                || e.excerpt().contains("modifica") || e.excerpt().contains("Alteração")));
        for (var evidence : result.evidences()) {
            assertEquals(contentText.substring(evidence.startPosition(), evidence.endPosition()),
                    evidence.excerpt());
        }
    }

    @Test
    void naoDeveDependerDeJanelaAnteriorParaNegacao() {
        String contentText = "Esta NT não " + "introduz qualquer modificação nas definições existentes, ".repeat(3)
                + "altera o leiaute da NF-e.";
        assertNotEquals(ImpactLevel.HIGH, analisarTexto(contentText).impactLevel());
    }

    @Test
    void naoDeveJuntarMetadadosIndependentesParaAfirmarMudanca() {
        assertNotEquals(ImpactLevel.HIGH,
                analyzer.analyze(criarInput("Esta NT altera", "o schema XML da NF-e")).impactLevel());
    }

    @Test
    void deveManterNiveisDasRegrasSemPromoverOuRebaixarPeloGlobal() {
        var high = new TechnicalImpactResult("Mudança estrutural", "Mudança confirmada de schema",
                "Fiscal", "NF-e", ImpactLevel.HIGH);
        var medium = new TechnicalImpactResult("Mudança CFOP", "Mudança específica de CFOP",
                "Fiscal", "NF-e", ImpactLevel.MEDIUM);
        var lowAction = new ActionItemResult("Conferir CFOP", "Conferência", "Analista fiscal",
                ImpactLevel.LOW, null);
        var evidence = new EvidenceResult("NT", "https://example.com/documento.pdf",
                null, null, "Esta NT altera o schema XML.", null, null);
        var match = new RuleMatch("regras-de-teste", List.of(high, medium), List.of(lowAction),
                List.of(evidence), null, null);
        var customAnalyzer = new RuleBasedImpactAnalyzer(
                new FiscalRuleEngine(List.of(context -> Optional.of(match))), context -> List.of());

        ImpactAnalysisResult result = customAnalyzer.analyze(criarInput("Comunicado", null));
        assertEquals(ImpactLevel.HIGH, result.impactLevel());
        assertEquals(List.of(high, medium), result.technicalImpacts());
        assertEquals(List.of(lowAction), result.actionItems());
    }

    @Test
    void deveManterCfopMediumQuandoHaMudancaEstruturalHighIndependente() {
        ImpactAnalysisResult result = analisarTexto(
                "Esta NT altera o leiaute da NF-e.\n\n"
                        + "Esta Nota Técnica altera a regra de validação I08-140 da NF-e, modelo 55, "
                        + "para permitir a utilização dos CFOP 1.949 e 2.949.");
        assertEquals(ImpactLevel.HIGH, result.impactLevel());
        assertEquals(ImpactLevel.MEDIUM, result.technicalImpacts().stream()
                .filter(i -> i.title().contains("CFOP")).findFirst().orElseThrow().impactLevel());
        assertEquals(ImpactLevel.MEDIUM, result.actionItems().stream()
                .filter(a -> a.title().contains("CFOP")).findFirst().orElseThrow().priority());
    }

    private ImpactAnalysisResult analisarTexto(String contentText) {
        return analyzer.analyze(criarInput("Comunicado fiscal", null,
                criarDocumento(ExtractionStatus.EXTRACTED, "https://example.com/documento.pdf", contentText)));
    }

    @Test
    void deveExplicitarLimiteDoFallbackSemAfirmarBaixoImpactoConfirmado() {
        ImpactAnalysisResult result = analisarTexto("Referência ao leiaute da NF-e sem informação de mudança.");
        assertNotEquals(ImpactLevel.HIGH, result.impactLevel());
        assertTrue(result.summary().contains("Classificação conservadora"));
        assertTrue(result.summary().contains("revisão manual"));
        assertTrue(result.technicalImpacts().getFirst().description()
                .contains("não uma confirmação de baixo impacto"));
    }

    @Test
    void deveRetornarAnaliseDeterministicaParaPublicacaoComSchema() {

        PublicationAnalysisInput input = criarInput(
                "Nota Tecnica com schema XML",
                "Publicacao altera schema da NF-e"
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(
                "Analise baseada em regras para a publicacao: "
                        + "Nota Tecnica com schema XML",
                result.summary()
        );
        assertEquals(ImpactLevel.HIGH, result.impactLevel());
        assertNull(result.homologationDeadline());
        assertNull(result.productionDeadline());

        assertEquals(1, result.technicalImpacts().size());
        assertEquals(
                "Revisar alteração de leiaute ou schema",
                result.technicalImpacts().get(0).title()
        );
        assertEquals(
                "Leiaute / schema",
                result.technicalImpacts().get(0).affectedComponent()
        );
        assertEquals(
                ImpactLevel.HIGH,
                result.technicalImpacts().get(0).impactLevel()
        );

        assertEquals(1, result.actionItems().size());
        assertEquals(
                "Desenvolvedor fiscal",
                result.actionItems().get(0).targetProfessionalProfile()
        );
        assertEquals(ImpactLevel.HIGH, result.actionItems().get(0).priority());

        assertEquals(1, result.evidences().size());
        assertEquals(
                "Publicacao altera schema da NF-e",
                result.evidences().get(0).excerpt()
        );
        assertEquals(
                "https://example.com/nota-tecnica.pdf",
                result.evidences().get(0).sourceUrl()
        );
        assertNull(result.evidences().getFirst().startPosition());
        assertNull(result.evidences().getFirst().endPosition());
    }

    @Test
    void deveUsarTituloComoEvidenciaQuandoDescricaoNaoExistir() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                null
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(
                "Comunicado fiscal",
                result.evidences().get(0).excerpt()
        );
    }

    @Test
    void deveUsarContentTextExtraidoParaDefinirImpacto() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        "O documento oficial altera o layout da NF-e."
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.HIGH, result.impactLevel());
    }

    @Test
    void deveUsarMatchDoMotorDeRegrasParaResultadoEspecifico() {

        PublicationAnalysisInput input = criarInput(
                "Nota Técnica 2026.009",
                null,
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        "A publicação altera regra de validação de CFOP "
                                + "aplicada à NF-e."
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(
                "Revisar regra de validação de CFOP",
                result.technicalImpacts().get(0).title()
        );
        assertEquals("NF-e", result.technicalImpacts().get(0).affectedComponent());
        assertEquals(
                "Revisar validação de CFOP",
                result.actionItems().get(0).title()
        );
        assertTrue(result.evidences().get(0).excerpt().contains("CFOP"));
    }

    @Test
    void deveGerarEvidenceDocumentalQuandoRegraEstiverNoContentText() {

        String contentText = "Cabeçalho informativo.\n\n"
                + "O documento oficial altera o layout da NF-e."
                + "\n\nRodapé informativo.";
        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        contentText
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        var evidence = result.evidences().get(0);

        assertEquals("Comunicado fiscal", evidence.sourceTitle());
        assertEquals("https://example.com/documento.pdf", evidence.sourceUrl());
        assertNull(evidence.documentSection());
        assertNull(evidence.pageNumber());
        assertTrue(evidence.excerpt().contains("layout"));
        assertTrue(evidence.excerpt().length() < contentText.length());
        assertEquals(
                contentText.substring(
                        evidence.startPosition(),
                        evidence.endPosition()
                ),
                evidence.excerpt()
        );
    }

    @Test
    void deveUsarDownloadUrlQuandoSourceUrlDocumentalForNull() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        null,
                        "O documento oficial altera o layout da NF-e."
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(
                "https://example.com/nota-tecnica.pdf",
                result.evidences().get(0).sourceUrl()
        );
    }

    @Test
    void deveManterComportamentoAnteriorQuandoDocumentForNull() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa"
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(
                "Publicacao informativa",
                result.evidences().get(0).excerpt()
        );
        assertNull(result.evidences().get(0).startPosition());
        assertNull(result.evidences().get(0).endPosition());
    }

    @Test
    void deveIgnorarContentTextQuandoDocumentoEstiverPending() {

        assertDocumentoNaoExtraidoUsaFallback(ExtractionStatus.PENDING);
    }

    @Test
    void deveIgnorarContentTextQuandoDocumentoEstiverFailed() {

        assertDocumentoNaoExtraidoUsaFallback(ExtractionStatus.FAILED);
    }

    @Test
    void deveIgnorarContentTextQuandoDocumentoEstiverEmpty() {

        assertDocumentoNaoExtraidoUsaFallback(ExtractionStatus.EMPTY);
    }

    @Test
    void deveUsarFallbackQuandoDocumentoExtractedTiverContentTextVazio() {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        "   "
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(
                "Publicacao informativa",
                result.evidences().get(0).excerpt()
        );
        assertEquals(
                "https://example.com/nota-tecnica.pdf",
                result.evidences().get(0).sourceUrl()
        );
    }

    @Test
    void deveEntregarFiscalChangesDetectadasParaFiscalRule() {
        FiscalChange fiscalChange = fiscalChangeRepresentativa();
        AtomicReference<FiscalAnalysisContext> capturedContext =
                new AtomicReference<>();
        RuleBasedImpactAnalyzer analyzerWithFakeDetector =
                new RuleBasedImpactAnalyzer(
                        new FiscalRuleEngine(List.of(context -> {
                            capturedContext.set(context);
                            return Optional.empty();
                        })),
                        context -> List.of(fiscalChange)
                );

        analyzerWithFakeDetector.analyze(criarInput(
                "Nota Técnica 2026.009",
                null,
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        "Texto oficial extraido."
                )
        ));

        assertEquals(
                List.of(fiscalChange),
                capturedContext.get().fiscalChanges()
        );
        assertEquals(
                "Texto oficial extraido.",
                capturedContext.get().documentContext().documentContentText()
        );
    }

    @Test
    void deveEntregarListaVaziaQuandoDetectorNaoEncontrarFiscalChanges() {
        AtomicReference<FiscalAnalysisContext> capturedContext =
                new AtomicReference<>();
        FiscalChangeDetector emptyDetector = context -> List.of();
        RuleBasedImpactAnalyzer analyzerWithEmptyDetector =
                new RuleBasedImpactAnalyzer(
                        new FiscalRuleEngine(List.of(context -> {
                            capturedContext.set(context);
                            return Optional.empty();
                        })),
                        emptyDetector
                );

        ImpactAnalysisResult result = analyzerWithEmptyDetector.analyze(
                criarInput("Comunicado fiscal", "Publicacao informativa")
        );

        assertTrue(capturedContext.get().fiscalChanges().isEmpty());
        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(
                "Revisar impacto tecnico da publicacao",
                result.technicalImpacts().get(0).title()
        );
    }

    @Test
    void deveGerarImpactoCfopAPartirDeFiscalChangeDetectadoNaNt2026009() {
        String contentText = "Esta Nota Técnica altera a regra de validação "
                + "I08-140 da Nota Fiscal Eletrônica (NF-e), modelo 55, "
                + "para permitir a utilização dos CFOP 1.949 e 2.949 em "
                + "todas as situações abrangidas pela própria regra.";

        ImpactAnalysisResult result = analyzer.analyze(criarInput(
                "Nota Técnica 2026.009 v.1.00",
                null,
                criarDocumento(
                        ExtractionStatus.EXTRACTED,
                        "https://example.com/documento.pdf",
                        contentText
                )
        ));

        assertEquals(
                1,
                result.technicalImpacts().stream()
                        .filter(technicalImpact -> technicalImpact.title()
                                .equals("Revisar regra de validação de CFOP"))
                        .count()
        );
        assertEquals(
                "NF-e modelo 55",
                result.technicalImpacts().getFirst().affectedComponent()
        );
        assertEquals(
                "Verificar implementação da regra I08-140 para os CFOP "
                        + "1.949 e 2.949.",
                result.actionItems().getFirst().description()
        );
        assertEquals(1, result.evidences().size());
        assertEquals(contentText, result.evidences().getFirst().excerpt());
        assertEquals(ImpactLevel.MEDIUM, result.impactLevel());
        assertEquals(ImpactLevel.MEDIUM, result.technicalImpacts().getFirst().impactLevel());
        assertEquals(ImpactLevel.MEDIUM, result.actionItems().getFirst().priority());
        assertEquals(
                contentText.substring(
                        result.evidences().getFirst().startPosition(),
                        result.evidences().getFirst().endPosition()
                ),
                result.evidences().getFirst().excerpt()
        );
    }

    private void assertDocumentoNaoExtraidoUsaFallback(
            ExtractionStatus extractionStatus
    ) {

        PublicationAnalysisInput input = criarInput(
                "Comunicado fiscal",
                "Publicacao informativa",
                criarDocumento(
                        extractionStatus,
                        "https://example.com/documento.pdf",
                        "O documento altera o schema e o layout."
                )
        );

        ImpactAnalysisResult result = analyzer.analyze(input);

        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(
                "Publicacao informativa",
                result.evidences().get(0).excerpt()
        );
        assertEquals(
                "https://example.com/nota-tecnica.pdf",
                result.evidences().get(0).sourceUrl()
        );
        assertNull(result.evidences().get(0).startPosition());
        assertNull(result.evidences().get(0).endPosition());
    }

    private PublicationAnalysisInput criarInput(
            String title,
            String description
    ) {

        return criarInput(title, description, null);
    }

    private PublicationAnalysisInput criarInput(
            String title,
            String description,
            PublicationDocumentAnalysisInput document
    ) {

        return new PublicationAnalysisInput(
                1L,
                "external-1",
                "SVRS",
                title,
                DocumentType.NOTA_TECNICA,
                LocalDateTime.of(2026, 9, 28, 10, 0),
                null,
                description,
                "https://example.com/nota-tecnica.pdf",
                document
        );
    }

    private PublicationDocumentAnalysisInput criarDocumento(
            ExtractionStatus extractionStatus,
            String sourceUrl,
            String contentText
    ) {

        return new PublicationDocumentAnalysisInput(
                sourceUrl,
                contentText,
                "a".repeat(64),
                contentText == null ? null : contentText.length(),
                extractionStatus,
                extractionStatus == ExtractionStatus.FAILED
                        ? "Falha na extracao"
                        : null,
                "svrs-pypdf-v1",
                LocalDateTime.of(2026, 9, 29, 10, 0)
        );
    }

    private FiscalChange fiscalChangeRepresentativa() {
        return new FiscalChange(
                FiscalChangeType.VALIDATION_RULE_CHANGE,
                "I08-140",
                "CFOP",
                "NF-e modelo 55",
                List.of("1.949", "2.949"),
                "Alteração da regra de validação I08-140 para permitir "
                        + "os CFOP 1.949 e 2.949 na NF-e modelo 55.",
                new EvidenceResult(
                        "Nota Técnica 2026.009 v.1.00",
                        "https://example.com/documento.pdf",
                        null,
                        null,
                        "Esta Nota Técnica altera a regra de validação I08-140.",
                        10,
                        67
                )
        );
    }
}
