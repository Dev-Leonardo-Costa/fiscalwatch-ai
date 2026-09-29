package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ActionItemResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalysisResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalyzer;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.TechnicalImpactResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ActionItemRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.EvidenceRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.TechnicalImpactRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.impactanalysis.exception.ImpactAnalysisNotFoundException;
import br.com.fiscalwatch.fiscalservice.impactanalysis.mapper.ImpactAnalysisMapper;
import br.com.fiscalwatch.fiscalservice.impactanalysis.repository.ImpactAnalysisRepository;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.ImpactAnalysisService;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationNotFoundException;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpactAnalysisServiceTest {

    @Mock
    private ImpactAnalysisRepository impactAnalysisRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationDocumentRepository publicationDocumentRepository;

    @Mock
    private ImpactAnalysisMapper impactAnalysisMapper;

    @Mock
    private ImpactAnalyzer impactAnalyzer;

    private ImpactAnalysisService impactAnalysisService;

    @BeforeEach
    void configurar() {
        impactAnalysisService = new ImpactAnalysisService(
                impactAnalysisRepository,
                publicationRepository,
                publicationDocumentRepository,
                impactAnalysisMapper,
                impactAnalyzer
        );
    }

    @Test
    void deveCriarAnaliseComPublicacaoExistente() {

        Long publicationId = 10L;
        PublicationEntity publication = criarPublicacao(publicationId);
        ImpactAnalysisRequest request = criarRequest(publicationId);
        ImpactAnalysisResponse response = criarResponse(1L, publicationId);

        when(publicationRepository.findById(publicationId))
                .thenReturn(Optional.of(publication));
        when(impactAnalysisRepository.save(any(ImpactAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(impactAnalysisMapper.toResponse(any(ImpactAnalysis.class)))
                .thenReturn(response);

        ImpactAnalysisResponse result = impactAnalysisService.create(request);

        ArgumentCaptor<ImpactAnalysis> captor =
                ArgumentCaptor.forClass(ImpactAnalysis.class);

        verify(publicationRepository).findById(publicationId);
        verify(impactAnalysisRepository).save(captor.capture());
        verify(impactAnalysisMapper).toResponse(captor.getValue());

        ImpactAnalysis saved = captor.getValue();

        assertSame(publication, saved.getPublication());
        assertEquals(request.summary(), saved.getSummary());
        assertEquals(ImpactLevel.HIGH, saved.getImpactLevel());
        assertEquals(AnalysisStatus.COMPLETED, saved.getStatus());
        assertEquals(request.analysisVersion(), saved.getAnalysisVersion());
        assertEquals(
                request.homologationDeadline(),
                saved.getHomologationDeadline()
        );
        assertEquals(
                request.productionDeadline(),
                saved.getProductionDeadline()
        );
        assertNotNull(saved.getAnalyzedAt());

        assertEquals(1, saved.getTechnicalImpacts().size());
        assertSame(
                saved,
                saved.getTechnicalImpacts().get(0).getImpactAnalysis()
        );
        assertEquals(
                "Atualizacao de schema",
                saved.getTechnicalImpacts().get(0).getTitle()
        );

        assertEquals(1, saved.getActionItems().size());
        assertSame(saved, saved.getActionItems().get(0).getImpactAnalysis());
        assertEquals(
                "Desenvolvedor fiscal",
                saved.getActionItems().get(0).getTargetProfessionalProfile()
        );

        assertEquals(1, saved.getEvidences().size());
        assertSame(saved, saved.getEvidences().get(0).getImpactAnalysis());
        assertEquals(
                "Trecho oficial da publicacao",
                saved.getEvidences().get(0).getExcerpt()
        );

        assertSame(response, result);
    }

    @Test
    void deveLancarExcecaoAoCriarAnaliseComPublicacaoInexistente() {

        Long publicationId = 10L;
        ImpactAnalysisRequest request = criarRequest(publicationId);

        when(publicationRepository.findById(publicationId))
                .thenReturn(Optional.empty());

        assertThrows(
                PublicationNotFoundException.class,
                () -> impactAnalysisService.create(request)
        );

        verify(impactAnalysisRepository, never()).save(any());
        verify(impactAnalysisMapper, never()).toResponse(any(ImpactAnalysis.class));
    }

    @Test
    void deveBuscarAnalisePorIdExistente() {

        Long id = 1L;
        ImpactAnalysis impactAnalysis = new ImpactAnalysis();
        ImpactAnalysisResponse response = criarResponse(id, 10L);

        when(impactAnalysisRepository.findById(id))
                .thenReturn(Optional.of(impactAnalysis));
        when(impactAnalysisMapper.toResponse(impactAnalysis))
                .thenReturn(response);

        ImpactAnalysisResponse result = impactAnalysisService.findById(id);

        verify(impactAnalysisRepository).findById(id);
        verify(impactAnalysisMapper).toResponse(impactAnalysis);
        assertSame(response, result);
    }

    @Test
    void deveLancarExcecaoAoBuscarAnalisePorIdInexistente() {

        Long id = 1L;

        when(impactAnalysisRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                ImpactAnalysisNotFoundException.class,
                () -> impactAnalysisService.findById(id)
        );

        verify(impactAnalysisMapper, never()).toResponse(any(ImpactAnalysis.class));
    }

    @Test
    void deveBuscarAnalisesPorPublicationId() {

        Long publicationId = 10L;
        ImpactAnalysis first = new ImpactAnalysis();
        ImpactAnalysis second = new ImpactAnalysis();
        ImpactAnalysisResponse firstResponse = criarResponse(1L, publicationId);
        ImpactAnalysisResponse secondResponse = criarResponse(2L, publicationId);

        when(publicationRepository.existsById(publicationId))
                .thenReturn(true);
        when(impactAnalysisRepository.findByPublicationId(publicationId))
                .thenReturn(List.of(first, second));
        when(impactAnalysisMapper.toResponse(first))
                .thenReturn(firstResponse);
        when(impactAnalysisMapper.toResponse(second))
                .thenReturn(secondResponse);

        List<ImpactAnalysisResponse> result =
                impactAnalysisService.findByPublicationId(publicationId);

        verify(publicationRepository).existsById(publicationId);
        verify(impactAnalysisRepository).findByPublicationId(publicationId);

        assertEquals(List.of(firstResponse, secondResponse), result);
    }

    @Test
    void deveGerarAnaliseAutomaticaComPublicacaoExistente() {

        Long publicationId = 10L;
        PublicationEntity publication = criarPublicacao(publicationId);
        ImpactAnalysisResult analysisResult = criarResultadoAnalise();
        ImpactAnalysisResponse response = criarResponse(1L, publicationId);

        when(publicationRepository.findById(publicationId))
                .thenReturn(Optional.of(publication));
        when(publicationDocumentRepository.findByPublicationId(publicationId))
                .thenReturn(Optional.empty());
        when(impactAnalyzer.analyze(any(PublicationAnalysisInput.class)))
                .thenReturn(analysisResult);
        when(impactAnalysisRepository.save(any(ImpactAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(impactAnalysisMapper.toResponse(any(ImpactAnalysis.class)))
                .thenReturn(response);

        ImpactAnalysisResponse result =
                impactAnalysisService.analyzePublication(publicationId);

        ArgumentCaptor<PublicationAnalysisInput> inputCaptor =
                ArgumentCaptor.forClass(PublicationAnalysisInput.class);
        ArgumentCaptor<ImpactAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(ImpactAnalysis.class);

        verify(publicationRepository).findById(publicationId);
        verify(impactAnalyzer).analyze(inputCaptor.capture());
        verify(impactAnalysisRepository).save(analysisCaptor.capture());
        verify(impactAnalysisMapper).toResponse(analysisCaptor.getValue());

        PublicationAnalysisInput input = inputCaptor.getValue();

        assertEquals(publication.getId(), input.id());
        assertEquals(publication.getExternalId(), input.externalId());
        assertEquals(publication.getSource(), input.source());
        assertEquals(publication.getTitle(), input.title());
        assertEquals(publication.getDocumentType(), input.documentType());
        assertEquals(publication.getPublishedAt(), input.publishedAt());
        assertEquals(publication.getModifiedAt(), input.modifiedAt());
        assertEquals(publication.getDescription(), input.description());
        assertEquals(publication.getDownloadUrl(), input.downloadUrl());
        assertNull(input.document());

        ImpactAnalysis saved = analysisCaptor.getValue();

        assertSame(publication, saved.getPublication());
        assertEquals(analysisResult.summary(), saved.getSummary());
        assertEquals(analysisResult.impactLevel(), saved.getImpactLevel());
        assertEquals(AnalysisStatus.COMPLETED, saved.getStatus());
        assertEquals("automatic-v1", saved.getAnalysisVersion());
        assertEquals(
                analysisResult.homologationDeadline(),
                saved.getHomologationDeadline()
        );
        assertEquals(
                analysisResult.productionDeadline(),
                saved.getProductionDeadline()
        );
        assertNotNull(saved.getAnalyzedAt());

        assertEquals(1, saved.getTechnicalImpacts().size());
        assertSame(
                saved,
                saved.getTechnicalImpacts().get(0).getImpactAnalysis()
        );
        assertEquals(
                "Atualizar layout XML",
                saved.getTechnicalImpacts().get(0).getTitle()
        );

        assertEquals(1, saved.getActionItems().size());
        assertSame(saved, saved.getActionItems().get(0).getImpactAnalysis());
        assertEquals(
                "Desenvolvedor fiscal",
                saved.getActionItems().get(0).getTargetProfessionalProfile()
        );

        assertEquals(1, saved.getEvidences().size());
        assertSame(saved, saved.getEvidences().get(0).getImpactAnalysis());
        assertEquals(
                "Evidencia encontrada no documento oficial",
                saved.getEvidences().get(0).getExcerpt()
        );

        assertSame(response, result);
    }

    @Test
    void deveGerarAnaliseAutomaticaComDocumentoExtraido() {

        Long publicationId = 10L;
        PublicationEntity publication = criarPublicacao(publicationId);
        PublicationDocumentEntity document = criarDocumento(publication);
        ImpactAnalysisResult analysisResult = criarResultadoAnalise();
        ImpactAnalysisResponse response = criarResponse(1L, publicationId);

        when(publicationRepository.findById(publicationId))
                .thenReturn(Optional.of(publication));
        when(publicationDocumentRepository.findByPublicationId(publicationId))
                .thenReturn(Optional.of(document));
        when(impactAnalyzer.analyze(any(PublicationAnalysisInput.class)))
                .thenReturn(analysisResult);
        when(impactAnalysisRepository.save(any(ImpactAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(impactAnalysisMapper.toResponse(any(ImpactAnalysis.class)))
                .thenReturn(response);

        ImpactAnalysisResponse result =
                impactAnalysisService.analyzePublication(publicationId);

        ArgumentCaptor<PublicationAnalysisInput> inputCaptor =
                ArgumentCaptor.forClass(PublicationAnalysisInput.class);
        ArgumentCaptor<ImpactAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(ImpactAnalysis.class);

        verify(impactAnalyzer).analyze(inputCaptor.capture());
        verify(impactAnalysisRepository).save(analysisCaptor.capture());

        PublicationAnalysisInput input = inputCaptor.getValue();

        assertNotNull(input.document());
        assertEquals(document.getSourceUrl(), input.document().sourceUrl());
        assertEquals(document.getContentText(), input.document().contentText());
        assertEquals(document.getContentHash(), input.document().contentHash());
        assertEquals(
                document.getContentLength(),
                input.document().contentLength()
        );
        assertEquals(
                document.getExtractionStatus(),
                input.document().extractionStatus()
        );
        assertEquals(
                document.getExtractionError(),
                input.document().extractionError()
        );
        assertEquals(
                document.getExtractorVersion(),
                input.document().extractorVersion()
        );
        assertEquals(document.getExtractedAt(), input.document().extractedAt());

        ImpactAnalysis saved = analysisCaptor.getValue();

        assertSame(publication, saved.getPublication());
        assertEquals(analysisResult.summary(), saved.getSummary());
        assertEquals(1, saved.getTechnicalImpacts().size());
        assertEquals(1, saved.getActionItems().size());
        assertEquals(1, saved.getEvidences().size());
        assertSame(response, result);
    }

    @Test
    void deveNaoChamarAnalyzerAoGerarAnaliseParaPublicacaoInexistente() {

        Long publicationId = 10L;

        when(publicationRepository.findById(publicationId))
                .thenReturn(Optional.empty());

        assertThrows(
                PublicationNotFoundException.class,
                () -> impactAnalysisService.analyzePublication(publicationId)
        );

        verify(impactAnalyzer, never())
                .analyze(any(PublicationAnalysisInput.class));
        verify(impactAnalysisRepository, never()).save(any());
    }

    @Test
    void deveNaoSalvarQuandoAnalyzerLancarErro() {

        Long publicationId = 10L;
        PublicationEntity publication = criarPublicacao(publicationId);
        RuntimeException exception = new RuntimeException("falha no analyzer");

        when(publicationRepository.findById(publicationId))
                .thenReturn(Optional.of(publication));
        when(publicationDocumentRepository.findByPublicationId(publicationId))
                .thenReturn(Optional.empty());
        when(impactAnalyzer.analyze(any(PublicationAnalysisInput.class)))
                .thenThrow(exception);

        RuntimeException result = assertThrows(
                RuntimeException.class,
                () -> impactAnalysisService.analyzePublication(publicationId)
        );

        assertSame(exception, result);
        verify(impactAnalysisRepository, never()).save(any());
        verify(impactAnalysisMapper, never()).toResponse(any(ImpactAnalysis.class));
    }

    private ImpactAnalysisRequest criarRequest(Long publicationId) {

        return new ImpactAnalysisRequest(
                publicationId,
                "Publicacao altera regras fiscais relevantes",
                ImpactLevel.HIGH,
                "manual-v1",
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0),
                List.of(new TechnicalImpactRequest(
                        "Atualizacao de schema",
                        "Atualizar validacao XML",
                        "Documentos fiscais",
                        "NF-e",
                        ImpactLevel.HIGH
                )),
                List.of(new ActionItemRequest(
                        "Atualizar emissor",
                        "Adequar emissor fiscal",
                        "Desenvolvedor fiscal",
                        ImpactLevel.HIGH,
                        LocalDateTime.of(2026, 9, 30, 0, 0)
                )),
                List.of(new EvidenceRequest(
                        "Nota Tecnica",
                        "https://example.com/nota-tecnica.pdf",
                        "Secao 2",
                        4,
                        "Trecho oficial da publicacao",
                        10,
                        40
                ))
        );
    }

    private ImpactAnalysisResponse criarResponse(Long id, Long publicationId) {

        return new ImpactAnalysisResponse(
                id,
                publicationId,
                "Publicacao altera regras fiscais relevantes",
                ImpactLevel.HIGH,
                AnalysisStatus.COMPLETED,
                "manual-v1",
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 9, 28, 12, 0),
                List.of(),
                List.of(),
                List.of(),
                LocalDateTime.of(2026, 9, 28, 12, 0),
                LocalDateTime.of(2026, 9, 28, 12, 0)
        );
    }

    private PublicationEntity criarPublicacao(Long id) {

        PublicationEntity publication = new PublicationEntity();
        publication.setId(id);
        publication.setExternalId("external-1");
        publication.setSource("SVRS");
        publication.setTitle("Nota Tecnica 2026.001");
        publication.setDocumentType(DocumentType.NOTA_TECNICA);
        publication.setPublishedAt(LocalDateTime.of(2026, 9, 28, 10, 0));
        publication.setModifiedAt(LocalDateTime.of(2026, 9, 29, 10, 0));
        publication.setDescription("Publicacao altera layout XML");
        publication.setDownloadUrl("https://example.com/nota-tecnica.pdf");

        return publication;
    }

    private PublicationDocumentEntity criarDocumento(
            PublicationEntity publication
    ) {

        PublicationDocumentEntity document = new PublicationDocumentEntity();

        document.setPublication(publication);
        document.setSourceUrl("https://example.com/nota-tecnica.pdf");
        document.setContentText("Texto oficial extraido da publicacao.");
        document.setContentHash("a".repeat(64));
        document.setContentLength(document.getContentText().length());
        document.setExtractionStatus(ExtractionStatus.EXTRACTED);
        document.setExtractionError(null);
        document.setExtractorVersion("svrs-pypdf-v1");
        document.setExtractedAt(LocalDateTime.of(2026, 9, 29, 10, 0));

        return document;
    }

    private ImpactAnalysisResult criarResultadoAnalise() {

        return new ImpactAnalysisResult(
                "Analise automatica da publicacao",
                ImpactLevel.HIGH,
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0),
                List.of(new TechnicalImpactResult(
                        "Atualizar layout XML",
                        "Adequar parser fiscal",
                        "Documentos fiscais",
                        "NF-e",
                        ImpactLevel.HIGH
                )),
                List.of(new ActionItemResult(
                        "Ajustar emissor fiscal",
                        "Implementar alteracoes tecnicas",
                        "Desenvolvedor fiscal",
                        ImpactLevel.HIGH,
                        LocalDateTime.of(2026, 9, 30, 0, 0)
                )),
                List.of(new EvidenceResult(
                        "Nota Tecnica",
                        "https://example.com/nota-tecnica.pdf",
                        "Secao 3",
                        5,
                        "Evidencia encontrada no documento oficial",
                        15,
                        80
                ))
        );
    }
}
