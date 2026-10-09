package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ActionItemResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalysisResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalyzer;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.SchemaChangeAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.SchemaChangeImpactAnalyzer;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.TechnicalImpactResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ActionItemRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.EvidenceRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.SchemaChangeRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.SchemaComparisonRequest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import br.com.fiscalwatch.fiscalservice.impactanalysis.exception.InvalidPublicationDocumentException;
import br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher;

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

    @Mock
    private SchemaChangeImpactAnalyzer schemaChangeImpactAnalyzer;

    private ImpactAnalysisService impactAnalysisService;

    @BeforeEach
    void configurar() {
        impactAnalysisService = new ImpactAnalysisService(
                impactAnalysisRepository,
                publicationRepository,
                publicationDocumentRepository,
                impactAnalysisMapper,
                impactAnalyzer,
                schemaChangeImpactAnalyzer
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

        when(publicationRepository.findByIdForUpdate(publicationId))
                .thenReturn(Optional.of(publication));
        when(publicationDocumentRepository.findByPublicationId(publicationId))
                .thenReturn(Optional.of(criarDocumento(publication)));
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

        verify(publicationRepository).findByIdForUpdate(publicationId);
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
        assertNotNull(input.document());

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

        when(publicationRepository.findByIdForUpdate(publicationId))
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

        when(publicationRepository.findByIdForUpdate(publicationId))
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

        when(publicationRepository.findByIdForUpdate(publicationId))
                .thenReturn(Optional.of(publication));
        when(publicationDocumentRepository.findByPublicationId(publicationId))
                .thenReturn(Optional.of(criarDocumento(publication)));
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

    @Test
    void deveCriarAnaliseDeComparacaoDeSchemaComPublicacaoExistente() {

        Long publicationId = 10L;
        PublicationEntity publication = criarPublicacao(publicationId);
        SchemaComparisonRequest request = criarSchemaComparisonRequest();
        ImpactAnalysisResult analysisResult = criarResultadoAnalise();
        ImpactAnalysisResponse response = criarResponse(1L, publicationId);

        when(publicationRepository.findByExternalId(
                request.currentExternalId()
        )).thenReturn(Optional.of(publication));
        when(impactAnalysisRepository
                .findFirstByPublicationIdAndAnalysisVersionAndPreviousExternalIdAndComparisonHash(
                        eq(publicationId),
                        eq("schema-comparison-v2"),
                        eq(request.previousExternalId()),
                        anyString()
                )).thenReturn(Optional.empty());
        when(publicationDocumentRepository.findByPublicationId(publicationId))
                .thenReturn(Optional.empty());
        when(schemaChangeImpactAnalyzer.analyze(
                any(SchemaChangeAnalysisInput.class)
        )).thenReturn(analysisResult);
        when(impactAnalysisRepository.save(any(ImpactAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(impactAnalysisMapper.toResponse(any(ImpactAnalysis.class)))
                .thenReturn(response);

        ImpactAnalysisResponse result =
                impactAnalysisService.analyzeSchemaComparison(request);

        ArgumentCaptor<SchemaChangeAnalysisInput> inputCaptor =
                ArgumentCaptor.forClass(SchemaChangeAnalysisInput.class);
        ArgumentCaptor<ImpactAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(ImpactAnalysis.class);

        verify(publicationRepository)
                .findByExternalId(request.currentExternalId());
        verify(schemaChangeImpactAnalyzer).analyze(inputCaptor.capture());
        verify(impactAnalysisRepository).save(analysisCaptor.capture());

        SchemaChangeAnalysisInput input = inputCaptor.getValue();

        assertEquals(publication.getExternalId(), input.publication().externalId());
        assertEquals(request.currentExternalId(), input.currentExternalId());
        assertEquals(request.previousExternalId(), input.previousExternalId());
        assertEquals(request.currentVersion(), input.currentVersion());
        assertEquals(request.previousVersion(), input.previousVersion());
        assertEquals(request.changes(), input.changes());

        ImpactAnalysis saved = analysisCaptor.getValue();

        assertSame(publication, saved.getPublication());
        assertEquals("schema-comparison-v2", saved.getAnalysisVersion());
        assertEquals(request.previousExternalId(), saved.getPreviousExternalId());
        assertNotNull(saved.getComparisonHash());
        assertEquals(64, saved.getComparisonHash().length());
        assertEquals(analysisResult.summary(), saved.getSummary());
        assertEquals(1, saved.getTechnicalImpacts().size());
        assertEquals(1, saved.getActionItems().size());
        assertEquals(1, saved.getEvidences().size());
        assertSame(response, result);
    }

    @Test
    void analiseSchemaComparisonV2RepetidaPermaneceIdempotente() {

        Long publicationId = 10L;
        PublicationEntity publication = criarPublicacao(publicationId);
        SchemaComparisonRequest request = criarSchemaComparisonRequest();
        ImpactAnalysis existing = new ImpactAnalysis();
        ImpactAnalysisResponse response = criarResponse(1L, publicationId);

        existing.setAnalysisVersion("schema-comparison-v2");
        existing.setPreviousExternalId(request.previousExternalId());
        existing.setComparisonHash("a".repeat(64));

        when(publicationRepository.findByExternalId(
                request.currentExternalId()
        )).thenReturn(Optional.of(publication));
        when(impactAnalysisRepository
                .findFirstByPublicationIdAndAnalysisVersionAndPreviousExternalIdAndComparisonHash(
                        eq(publicationId),
                        eq("schema-comparison-v2"),
                        eq(request.previousExternalId()),
                        anyString()
                )).thenReturn(Optional.of(existing));
        when(impactAnalysisMapper.toResponse(existing)).thenReturn(response);

        ImpactAnalysisResponse result =
                impactAnalysisService.analyzeSchemaComparison(request);

        verify(schemaChangeImpactAnalyzer, never())
                .analyze(any(SchemaChangeAnalysisInput.class));
        verify(impactAnalysisRepository, never()).save(any());
        assertSame(response, result);
    }

    @Test
    void analiseSchemaComparisonV1ExistenteNaoBloqueiaCriacaoDaV2() {

        Long publicationId = 10L;
        PublicationEntity publication = criarPublicacao(publicationId);
        SchemaComparisonRequest request = criarSchemaComparisonRequest();
        ImpactAnalysis existingV1 = new ImpactAnalysis();
        ImpactAnalysisResult analysisResult = criarResultadoAnalise();
        ImpactAnalysisResponse response = criarResponse(1L, publicationId);

        existingV1.setAnalysisVersion("schema-comparison-v1");
        existingV1.setPreviousExternalId(request.previousExternalId());
        existingV1.setComparisonHash("a".repeat(64));

        when(publicationRepository.findByExternalId(
                request.currentExternalId()
        )).thenReturn(Optional.of(publication));
        when(impactAnalysisRepository
                .findFirstByPublicationIdAndAnalysisVersionAndPreviousExternalIdAndComparisonHash(
                        eq(publicationId),
                        eq("schema-comparison-v2"),
                        eq(request.previousExternalId()),
                        anyString()
                )).thenReturn(Optional.empty());
        when(publicationDocumentRepository.findByPublicationId(publicationId))
                .thenReturn(Optional.empty());
        when(schemaChangeImpactAnalyzer.analyze(
                any(SchemaChangeAnalysisInput.class)
        )).thenReturn(analysisResult);
        when(impactAnalysisRepository.save(any(ImpactAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(impactAnalysisMapper.toResponse(any(ImpactAnalysis.class)))
                .thenReturn(response);

        ImpactAnalysisResponse result =
                impactAnalysisService.analyzeSchemaComparison(request);

        ArgumentCaptor<ImpactAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(ImpactAnalysis.class);

        verify(impactAnalysisRepository)
                .findFirstByPublicationIdAndAnalysisVersionAndPreviousExternalIdAndComparisonHash(
                        eq(publicationId),
                        eq("schema-comparison-v2"),
                        eq(request.previousExternalId()),
                        anyString()
                );
        verify(impactAnalysisRepository, never())
                .findFirstByPublicationIdAndAnalysisVersionAndPreviousExternalIdAndComparisonHash(
                        eq(publicationId),
                        eq("schema-comparison-v1"),
                        eq(request.previousExternalId()),
                        anyString()
                );
        verify(schemaChangeImpactAnalyzer)
                .analyze(any(SchemaChangeAnalysisInput.class));
        verify(impactAnalysisRepository).save(analysisCaptor.capture());

        ImpactAnalysis saved = analysisCaptor.getValue();

        assertEquals("schema-comparison-v2", saved.getAnalysisVersion());
        assertEquals(request.previousExternalId(), saved.getPreviousExternalId());
        assertNotNull(saved.getComparisonHash());
        assertSame(response, result);
    }

    @Test
    void comparacaoDiferenteGeraNovaAnalise() {

        Long publicationId = 10L;
        PublicationEntity publication = criarPublicacao(publicationId);
        SchemaComparisonRequest primeira = criarSchemaComparisonRequest();
        SchemaComparisonRequest segunda = criarSchemaComparisonRequestComChange(
                "CARDINALITY_CHANGED",
                "complexType:TCredPresIBSZFM/element:vCredPresIBSZFM",
                "vCredPresIBSZFM",
                "{minOccurs=0, maxOccurs=1}",
                "{minOccurs=1, maxOccurs=1}"
        );
        ImpactAnalysisResult analysisResult = criarResultadoAnalise();
        ImpactAnalysisResponse response = criarResponse(1L, publicationId);

        when(publicationRepository.findByExternalId("external-1"))
                .thenReturn(Optional.of(publication));
        when(impactAnalysisRepository
                .findFirstByPublicationIdAndAnalysisVersionAndPreviousExternalIdAndComparisonHash(
                        eq(publicationId),
                        eq("schema-comparison-v2"),
                        eq("external-0"),
                        anyString()
                )).thenReturn(Optional.empty());
        when(publicationDocumentRepository.findByPublicationId(publicationId))
                .thenReturn(Optional.empty());
        when(schemaChangeImpactAnalyzer.analyze(
                any(SchemaChangeAnalysisInput.class)
        )).thenReturn(analysisResult);
        when(impactAnalysisRepository.save(any(ImpactAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(impactAnalysisMapper.toResponse(any(ImpactAnalysis.class)))
                .thenReturn(response);

        impactAnalysisService.analyzeSchemaComparison(primeira);
        impactAnalysisService.analyzeSchemaComparison(segunda);

        ArgumentCaptor<ImpactAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(ImpactAnalysis.class);

        verify(impactAnalysisRepository, times(2))
                .save(analysisCaptor.capture());

        List<ImpactAnalysis> saved = analysisCaptor.getAllValues();

        assertEquals("external-0", saved.get(0).getPreviousExternalId());
        assertEquals("external-0", saved.get(1).getPreviousExternalId());
        assertNotNull(saved.get(0).getComparisonHash());
        assertNotNull(saved.get(1).getComparisonHash());
        assertEquals(64, saved.get(0).getComparisonHash().length());
        assertEquals(64, saved.get(1).getComparisonHash().length());
        assertNotNull(saved.get(0).getComparisonHash());
        assertEquals(
                false,
                saved.get(0).getComparisonHash()
                        .equals(saved.get(1).getComparisonHash())
        );
    }

    @Test
    void comparacaoComVersaoAnteriorDiferenteGeraNovaAnalise() {

        Long publicationId = 10L;
        PublicationEntity publication = criarPublicacao(publicationId);
        SchemaComparisonRequest primeira = criarSchemaComparisonRequest();
        SchemaComparisonRequest segunda = criarSchemaComparisonRequestComAnterior(
                "external-anterior-2",
                "2025.002 v1.10"
        );
        ImpactAnalysisResult analysisResult = criarResultadoAnalise();
        ImpactAnalysisResponse response = criarResponse(1L, publicationId);

        when(publicationRepository.findByExternalId("external-1"))
                .thenReturn(Optional.of(publication));
        when(impactAnalysisRepository
                .findFirstByPublicationIdAndAnalysisVersionAndPreviousExternalIdAndComparisonHash(
                        eq(publicationId),
                        eq("schema-comparison-v2"),
                        anyString(),
                        anyString()
                )).thenReturn(Optional.empty());
        when(publicationDocumentRepository.findByPublicationId(publicationId))
                .thenReturn(Optional.empty());
        when(schemaChangeImpactAnalyzer.analyze(
                any(SchemaChangeAnalysisInput.class)
        )).thenReturn(analysisResult);
        when(impactAnalysisRepository.save(any(ImpactAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(impactAnalysisMapper.toResponse(any(ImpactAnalysis.class)))
                .thenReturn(response);

        impactAnalysisService.analyzeSchemaComparison(primeira);
        impactAnalysisService.analyzeSchemaComparison(segunda);

        ArgumentCaptor<ImpactAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(ImpactAnalysis.class);

        verify(impactAnalysisRepository, times(2))
                .save(analysisCaptor.capture());

        List<ImpactAnalysis> saved = analysisCaptor.getAllValues();

        assertEquals("external-0", saved.get(0).getPreviousExternalId());
        assertEquals(
                "external-anterior-2",
                saved.get(1).getPreviousExternalId()
        );
        assertEquals(
                false,
                saved.get(0).getComparisonHash()
                        .equals(saved.get(1).getComparisonHash())
        );
    }

    @Test
    void deveLancarExcecaoAoAnalisarSchemaDePublicacaoInexistente() {

        SchemaComparisonRequest request = criarSchemaComparisonRequest();

        when(publicationRepository.findByExternalId(
                request.currentExternalId()
        )).thenReturn(Optional.empty());

        assertThrows(
                PublicationNotFoundException.class,
                () -> impactAnalysisService.analyzeSchemaComparison(request)
        );

        verify(schemaChangeImpactAnalyzer, never())
                .analyze(any(SchemaChangeAnalysisInput.class));
        verify(impactAnalysisRepository, never()).save(any());
    }

    @Test
    void deveRetornarAnaliseDocumentalConcluidaAntesDeConsultarDocumento() {
        PublicationEntity publication = criarPublicacao(10L);
        ImpactAnalysis existing = new ImpactAnalysis();
        existing.setId(3L);
        existing.setPublication(publication);
        existing.setAnalysisVersion("automatic-v1");
        existing.setStatus(AnalysisStatus.COMPLETED);
        var response = criarResponse(3L, 10L);
        when(publicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(publication));
        when(impactAnalysisRepository
                .findFirstByPublicationIdAndAnalysisVersionAndStatusOrderByIdAsc(
                        10L, "automatic-v1", AnalysisStatus.COMPLETED))
                .thenReturn(Optional.of(existing));
        when(impactAnalysisMapper.toResponse(existing)).thenReturn(response);

        assertSame(response, impactAnalysisService.analyzePublication(10L));
        var order = inOrder(publicationRepository, impactAnalysisRepository);
        order.verify(publicationRepository).findByIdForUpdate(10L);
        order.verify(impactAnalysisRepository)
                .findFirstByPublicationIdAndAnalysisVersionAndStatusOrderByIdAsc(
                        10L, "automatic-v1", AnalysisStatus.COMPLETED);
        verify(publicationDocumentRepository, never()).findByPublicationId(any());
        verify(impactAnalyzer, never()).analyze(any());
        verify(impactAnalysisRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ExtractionStatus.class, names = {"FAILED", "PENDING", "EMPTY"})
    void naoDeveAnalisarDocumentoSemExtracaoValida(ExtractionStatus status) {
        var publication = criarPublicacao(10L);
        var document = criarDocumento(publication);
        document.setExtractionStatus(status);
        assertDocumentoRejeitado(publication, document);
    }

    @ParameterizedTest
    @ValueSource(strings = {"semDocumento", "textoNulo", "textoVazio", "textoEmBranco",
            "hashAusente", "hashInvalido", "hashDiferente", "comprimentoAusente",
            "comprimentoDiferente", "comprimentoNegativo", "erroExtracao", "unicodeInvalido"})
    void naoDeveAnalisarDocumentoVazioOuInconsistente(String caso) {
        var publication = criarPublicacao(10L);
        var document = criarDocumento(publication);
        switch (caso) {
            case "semDocumento" -> document = null;
            case "textoNulo" -> document.setContentText(null);
            case "textoVazio" -> document.setContentText("");
            case "textoEmBranco" -> document.setContentText(" \n\t");
            case "hashAusente" -> document.setContentHash(null);
            case "hashInvalido" -> document.setContentHash("hash");
            case "hashDiferente" -> document.setContentHash("a".repeat(64));
            case "comprimentoAusente" -> document.setContentLength(null);
            case "comprimentoDiferente" -> document.setContentLength(1);
            case "comprimentoNegativo" -> document.setContentLength(-1);
            case "erroExtracao" -> document.setExtractionError("extração parcial");
            case "unicodeInvalido" -> {
                document.setContentText("\uD800");
                document.setContentLength(1);
            }
            default -> throw new AssertionError(caso);
        }
        assertDocumentoRejeitado(publication, document);
    }

    @Test
    void deveAceitarUnicodeEHashMaiusculoCompativeisComPython() {
        var publication = criarPublicacao(10L);
        var document = criarDocumento(publication);
        String text = "Tributação \uD83D\uDE00";
        document.setContentText(text);
        document.setContentLength(text.codePointCount(0, text.length()));
        document.setContentHash(PublicationSnapshotHasher.calculateContentHash(text)
                .toUpperCase(java.util.Locale.ROOT));
        when(publicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(publication));
        when(publicationDocumentRepository.findByPublicationId(10L)).thenReturn(Optional.of(document));
        when(impactAnalyzer.analyze(any())).thenReturn(criarResultadoAnalise());
        when(impactAnalysisRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(impactAnalysisMapper.toResponse(any(ImpactAnalysis.class)))
                .thenReturn(criarResponse(1L, 10L));

        impactAnalysisService.analyzePublication(10L);

        verify(impactAnalysisRepository).save(any());
    }

    private void assertDocumentoRejeitado(PublicationEntity publication,
                                         PublicationDocumentEntity document) {
        when(publicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(publication));
        when(publicationDocumentRepository.findByPublicationId(10L))
                .thenReturn(Optional.ofNullable(document));
        assertThrows(InvalidPublicationDocumentException.class,
                () -> impactAnalysisService.analyzePublication(10L));
        verify(impactAnalyzer, never()).analyze(any());
        verify(impactAnalysisRepository, never()).save(any());
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

    private SchemaComparisonRequest criarSchemaComparisonRequest() {

        return new SchemaComparisonRequest(
                "external-1",
                "external-0",
                "2025.002 v1.30",
                "2025.002 v1.20",
                List.of(new SchemaChangeRequest(
                        "DFeTiposBasicos_v1.00.xsd",
                        "TYPE_CHANGED",
                        "complexType:TCIBS/element:vBC",
                        "vBC",
                        "TDec1302",
                        "TDec1302RTC",
                        null,
                        null
                ))
        );
    }

    private SchemaComparisonRequest criarSchemaComparisonRequestComChange(
            String changeType,
            String schemaPath,
            String symbolName,
            Object before,
            Object after
    ) {

        return new SchemaComparisonRequest(
                "external-1",
                "external-0",
                "2025.002 v1.30",
                "2025.002 v1.20",
                List.of(new SchemaChangeRequest(
                        "DFeTiposBasicos_v1.00.xsd",
                        changeType,
                        schemaPath,
                        symbolName,
                        before,
                        after,
                        null,
                        null
                ))
        );
    }

    private SchemaComparisonRequest criarSchemaComparisonRequestComAnterior(
            String previousExternalId,
            String previousVersion
    ) {

        return new SchemaComparisonRequest(
                "external-1",
                previousExternalId,
                "2025.002 v1.30",
                previousVersion,
                List.of(new SchemaChangeRequest(
                        "DFeTiposBasicos_v1.00.xsd",
                        "TYPE_CHANGED",
                        "complexType:TCIBS/element:vBC",
                        "vBC",
                        "TDec1302",
                        "TDec1302RTC",
                        null,
                        null
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
        document.setContentHash(br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher
                .calculateContentHash(document.getContentText()));
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
