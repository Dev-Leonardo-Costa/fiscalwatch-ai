package br.com.fiscalwatch.fiscalservice.impactanalysis.service;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ActionItemResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalysisResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalyzer;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationDocumentAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.TechnicalImpactResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ActionItemRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.EvidenceRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.TechnicalImpactRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ActionItem;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.Evidence;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.TechnicalImpact;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;
import br.com.fiscalwatch.fiscalservice.impactanalysis.exception.ImpactAnalysisNotFoundException;
import br.com.fiscalwatch.fiscalservice.impactanalysis.mapper.ImpactAnalysisMapper;
import br.com.fiscalwatch.fiscalservice.impactanalysis.repository.ImpactAnalysisRepository;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationNotFoundException;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ImpactAnalysisService {

    private final ImpactAnalysisRepository impactAnalysisRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationDocumentRepository publicationDocumentRepository;
    private final ImpactAnalysisMapper impactAnalysisMapper;
    private final ImpactAnalyzer impactAnalyzer;

    @Transactional(readOnly = true)
    public ImpactAnalysisResponse findById(Long id) {

        ImpactAnalysis impactAnalysis = impactAnalysisRepository
                .findById(id)
                .orElseThrow(
                        () -> new ImpactAnalysisNotFoundException(id)
                );

        return impactAnalysisMapper.toResponse(impactAnalysis);
    }

    @Transactional(readOnly = true)
    public List<ImpactAnalysisResponse> findByPublicationId(
            Long publicationId
    ) {

        if (!publicationRepository.existsById(publicationId)) {
            throw new PublicationNotFoundException(publicationId);
        }

        return impactAnalysisRepository
                .findByPublicationId(publicationId)
                .stream()
                .map(impactAnalysisMapper::toResponse)
                .toList();
    }

    @Transactional
    public ImpactAnalysisResponse create(ImpactAnalysisRequest request) {

        PublicationEntity publication = publicationRepository
                .findById(request.publicationId())
                .orElseThrow(
                        () -> new PublicationNotFoundException(
                                request.publicationId()
                        )
                );

        ImpactAnalysis impactAnalysis = new ImpactAnalysis();

        impactAnalysis.setPublication(publication);
        impactAnalysis.setSummary(request.summary());
        impactAnalysis.setImpactLevel(request.impactLevel());
        impactAnalysis.setStatus(AnalysisStatus.COMPLETED);
        impactAnalysis.setAnalysisVersion(request.analysisVersion());
        impactAnalysis.setHomologationDeadline(
                request.homologationDeadline()
        );
        impactAnalysis.setProductionDeadline(request.productionDeadline());
        impactAnalysis.setAnalyzedAt(LocalDateTime.now());

        addTechnicalImpacts(impactAnalysis, request.technicalImpacts());
        addActionItems(impactAnalysis, request.actionItems());
        addEvidences(impactAnalysis, request.evidences());

        ImpactAnalysis savedImpactAnalysis =
                impactAnalysisRepository.save(impactAnalysis);

        return impactAnalysisMapper.toResponse(savedImpactAnalysis);
    }

    @Transactional
    public ImpactAnalysisResponse analyzePublication(Long publicationId) {

        PublicationEntity publication = publicationRepository
                .findById(publicationId)
                .orElseThrow(
                        () -> new PublicationNotFoundException(publicationId)
                );

        PublicationDocumentAnalysisInput document =
                publicationDocumentRepository
                        .findByPublicationId(publicationId)
                        .map(this::toPublicationDocumentAnalysisInput)
                        .orElse(null);

        PublicationAnalysisInput input = toPublicationAnalysisInput(
                publication,
                document
        );
        ImpactAnalysisResult result = impactAnalyzer.analyze(input);

        ImpactAnalysis impactAnalysis = new ImpactAnalysis();

        impactAnalysis.setPublication(publication);
        impactAnalysis.setSummary(result.summary());
        impactAnalysis.setImpactLevel(result.impactLevel());
        impactAnalysis.setStatus(AnalysisStatus.COMPLETED);
        impactAnalysis.setAnalysisVersion("automatic-v1");
        impactAnalysis.setHomologationDeadline(
                result.homologationDeadline()
        );
        impactAnalysis.setProductionDeadline(result.productionDeadline());
        impactAnalysis.setAnalyzedAt(LocalDateTime.now());

        addTechnicalImpactResults(
                impactAnalysis,
                result.technicalImpacts()
        );
        addActionItemResults(impactAnalysis, result.actionItems());
        addEvidenceResults(impactAnalysis, result.evidences());

        ImpactAnalysis savedImpactAnalysis =
                impactAnalysisRepository.save(impactAnalysis);

        return impactAnalysisMapper.toResponse(savedImpactAnalysis);
    }

    private PublicationAnalysisInput toPublicationAnalysisInput(
            PublicationEntity publication,
            PublicationDocumentAnalysisInput document
    ) {

        return new PublicationAnalysisInput(
                publication.getId(),
                publication.getExternalId(),
                publication.getSource(),
                publication.getTitle(),
                publication.getDocumentType(),
                publication.getPublishedAt(),
                publication.getModifiedAt(),
                publication.getDescription(),
                publication.getDownloadUrl(),
                document
        );
    }

    private PublicationDocumentAnalysisInput toPublicationDocumentAnalysisInput(
            PublicationDocumentEntity document
    ) {

        return new PublicationDocumentAnalysisInput(
                document.getSourceUrl(),
                document.getContentText(),
                document.getContentHash(),
                document.getContentLength(),
                document.getExtractionStatus(),
                document.getExtractionError(),
                document.getExtractorVersion(),
                document.getExtractedAt()
        );
    }

    private void addTechnicalImpacts(
            ImpactAnalysis impactAnalysis,
            List<TechnicalImpactRequest> requests
    ) {

        if (requests == null) {
            return;
        }

        requests.forEach(request -> {
            TechnicalImpact technicalImpact = new TechnicalImpact();

            technicalImpact.setTitle(request.title());
            technicalImpact.setDescription(request.description());
            technicalImpact.setAffectedArea(request.affectedArea());
            technicalImpact.setAffectedComponent(
                    request.affectedComponent()
            );
            technicalImpact.setImpactLevel(request.impactLevel());

            impactAnalysis.addTechnicalImpact(technicalImpact);
        });
    }

    private void addActionItems(
            ImpactAnalysis impactAnalysis,
            List<ActionItemRequest> requests
    ) {

        if (requests == null) {
            return;
        }

        requests.forEach(request -> {
            ActionItem actionItem = new ActionItem();

            actionItem.setTitle(request.title());
            actionItem.setDescription(request.description());
            actionItem.setTargetProfessionalProfile(
                    request.targetProfessionalProfile()
            );
            actionItem.setPriority(request.priority());
            actionItem.setDueAt(request.dueAt());

            impactAnalysis.addActionItem(actionItem);
        });
    }

    private void addEvidences(
            ImpactAnalysis impactAnalysis,
            List<EvidenceRequest> requests
    ) {

        if (requests == null) {
            return;
        }

        requests.forEach(request -> {
            Evidence evidence = new Evidence();

            evidence.setSourceTitle(request.sourceTitle());
            evidence.setSourceUrl(request.sourceUrl());
            evidence.setDocumentSection(request.documentSection());
            evidence.setPageNumber(request.pageNumber());
            evidence.setExcerpt(request.excerpt());
            evidence.setStartPosition(request.startPosition());
            evidence.setEndPosition(request.endPosition());

            impactAnalysis.addEvidence(evidence);
        });
    }

    private void addTechnicalImpactResults(
            ImpactAnalysis impactAnalysis,
            List<TechnicalImpactResult> results
    ) {

        if (results == null) {
            return;
        }

        results.forEach(result -> {
            TechnicalImpact technicalImpact = new TechnicalImpact();

            technicalImpact.setTitle(result.title());
            technicalImpact.setDescription(result.description());
            technicalImpact.setAffectedArea(result.affectedArea());
            technicalImpact.setAffectedComponent(
                    result.affectedComponent()
            );
            technicalImpact.setImpactLevel(result.impactLevel());

            impactAnalysis.addTechnicalImpact(technicalImpact);
        });
    }

    private void addActionItemResults(
            ImpactAnalysis impactAnalysis,
            List<ActionItemResult> results
    ) {

        if (results == null) {
            return;
        }

        results.forEach(result -> {
            ActionItem actionItem = new ActionItem();

            actionItem.setTitle(result.title());
            actionItem.setDescription(result.description());
            actionItem.setTargetProfessionalProfile(
                    result.targetProfessionalProfile()
            );
            actionItem.setPriority(result.priority());
            actionItem.setDueAt(result.dueAt());

            impactAnalysis.addActionItem(actionItem);
        });
    }

    private void addEvidenceResults(
            ImpactAnalysis impactAnalysis,
            List<EvidenceResult> results
    ) {

        if (results == null) {
            return;
        }

        results.forEach(result -> {
            Evidence evidence = new Evidence();

            evidence.setSourceTitle(result.sourceTitle());
            evidence.setSourceUrl(result.sourceUrl());
            evidence.setDocumentSection(result.documentSection());
            evidence.setPageNumber(result.pageNumber());
            evidence.setExcerpt(result.excerpt());
            evidence.setStartPosition(result.startPosition());
            evidence.setEndPosition(result.endPosition());

            impactAnalysis.addEvidence(evidence);
        });
    }
}
