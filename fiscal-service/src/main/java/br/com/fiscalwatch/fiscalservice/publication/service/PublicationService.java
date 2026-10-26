package br.com.fiscalwatch.fiscalservice.publication.service;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationCollectionState;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.PublicationDocumentValidator;
import br.com.fiscalwatch.fiscalservice.impactanalysis.exception.InvalidPublicationDocumentException;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationDocumentHistoryResponse;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationHistoryResponse;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationResponse;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationSnapshotResponse;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationAlreadyExistsException;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationNotFoundException;
import br.com.fiscalwatch.fiscalservice.publication.mapper.PublicationMapper;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationDocumentEvent;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEvent;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@RequiredArgsConstructor
@Service
public class PublicationService {
    @Transactional(readOnly = true)
    public List<PublicationResponse> search(String query) {
        if (query == null || query.isBlank() || query.strip().length() > 200) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Informe uma pesquisa de 1 a 200 caracteres");
        }
        String term = query.strip().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return publicationRepository.search("%" + term + "%", org.springframework.data.domain.PageRequest.of(0, 50))
                .stream().map(publicationMapper::toResponse).toList();
    }


    @Transactional(readOnly = true)
    public PublicationCollectionState findSvrsCollectionState(String externalId) {
        var publication = publicationRepository.findByExternalId(externalId);
        if (publication.isEmpty() || publication.get().getRecordOrigin()
                != br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin.PUBLIC) {
            return new PublicationCollectionState(
                    externalId, false, null, false);
        }
        if (!"SVRS".equals(publication.get().getSource())) {
            throw new IllegalArgumentException("Identidade não pertence à SVRS");
        }
        var document = publicationDocumentRepository.findByPublicationId(publication.get().getId());
        boolean valid = false;
        if (document.isPresent()) {
            try {
                PublicationDocumentValidator.validate(document.get());
                valid = true;
            } catch (InvalidPublicationDocumentException ignored) {
                // EXTRACTED inválido não pode ser sobrescrito pelo contrato atual.
            }
        }
        return new PublicationCollectionState(
                externalId, true, document.map(PublicationDocumentEntity::getExtractionStatus).orElse(null), valid);
    }

    private static final String EXTERNAL_ID_UNIQUE_CONSTRAINT =
            "uk_publications_external_id";

    private final PublicationRepository publicationRepository;
    private final PublicationDocumentRepository publicationDocumentRepository;
    private final PublicationMapper publicationMapper;
    private final PublicationSnapshotService snapshotService;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public List<PublicationSnapshotResponse> findSnapshots(Long publicationId) {
        if (!publicationRepository.existsByIdAndRecordOrigin(publicationId,
                br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin.PUBLIC)) {
            throw new PublicationNotFoundException(publicationId);
        }
        return snapshotService.history(publicationId);
    }


    @Transactional
    public PublicationResponse create(PublicationRequest request) {

        if (publicationRepository.existsByExternalId(request.externalId())) {
            throw new PublicationAlreadyExistsException(request.externalId());
        }

        PublicationEntity entity = publicationMapper.toEntity(request);

        try {
            PublicationEntity savedEntity = publicationRepository.saveAndFlush(entity);

            return publicationMapper.toResponse(savedEntity);
        } catch (DataIntegrityViolationException exception) {
            if (isExternalIdUniqueConstraintViolation(exception)) {
                throw new PublicationAlreadyExistsException(request.externalId());
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PublicationResponse findById(Long id) {

        PublicationEntity entity = publicationRepository
                .findByIdAndRecordOrigin(id, br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin.PUBLIC)
                .orElseThrow(
                        () -> new PublicationNotFoundException(id)
                );

        return publicationMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<PublicationHistoryResponse> findHistory(
            String source,
            DocumentType documentType
    ) {

        return publicationDocumentRepository
                .findHistoryByPublication(source, documentType)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public void processEvent(PublicationEvent event) {
        // A first delivery can race before a publication/canonical identity exists.
        // Retry the WHOLE rolled-back transaction, never a failed INSERT in place.
        boolean joined = TransactionSynchronizationManager.isActualTransactionActive();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        for (int attempt = 0; ; attempt++) {
            try {
                transaction.executeWithoutResult(status -> processEventInTransaction(event));
                return;
            } catch (DataIntegrityViolationException failure) {
                if (joined || attempt >= 2 || !isIngestionIdentityConflict(failure)) throw failure;
            }
        }
    }

    private boolean isIngestionIdentityConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && (message.toLowerCase(java.util.Locale.ROOT).contains("uk_publications_external_id")
                    || message.toLowerCase(java.util.Locale.ROOT).contains("uk_technical_notes_identity")
                    || message.toLowerCase(java.util.Locale.ROOT).contains("uk_technical_note_revision"))) return true;
        }
        return false;
    }

    private void processEventInTransaction(PublicationEvent event) {

        PublicationRequest request = event.publication();

        PublicationEntity publication = publicationRepository
                .findByExternalIdForUpdate(request.externalId())
                .orElseGet(() -> publicationRepository.saveAndFlush(
                        publicationMapper.toEntity(request)
                ));

        PublicationDocumentEvent document = event.document();

        if (document == null) {
            return;
        }

        if (!publication.getSource().equals(request.source())) {
            throw new IllegalArgumentException("Fonte incompatível com a identidade da publicação");
        }

        // Capture BEFORE the legacy short-circuit: a reused URL may carry another version.
        if (document.extractionStatus() == ExtractionStatus.EXTRACTED) {
            var candidate = toDocumentEntity(publication, document);
            if (isValidSnapshotCandidate(candidate)) snapshotService.capture(publication, request, candidate);
        }

        PublicationDocumentEntity existingDocument = publicationDocumentRepository
                .findByPublicationId(publication.getId())
                .orElse(null);

        if (existingDocument != null) {
            if (canRecover(existingDocument, document)) {
                applyDocument(existingDocument, document);
                publicationDocumentRepository.saveAndFlush(existingDocument);
            }
            return;
        }

        publicationDocumentRepository.saveAndFlush(
                toDocumentEntity(publication, document)
        );
    }

    private boolean isValidSnapshotCandidate(PublicationDocumentEntity candidate) {
        try {
            PublicationDocumentValidator.validate(candidate);
            return true;
        } catch (InvalidPublicationDocumentException invalid) {
            // Retain the old extraction/error handling, but never audit invalid text as a valid snapshot.
            return false;
        }
    }

    /**
     * Recuperação monotônica por qualidade, não por horário de chegada.
     * Não substitui EXTRACTED/EMPTY nem implementa versões do documento.
     * O lock da publicação serializa a criação/recuperação do seu documento
     * mesmo quando ainda não existe uma linha em publication_documents.
     */
    private boolean canRecover(PublicationDocumentEntity existing,
                               PublicationDocumentEvent incoming) {
        return (existing.getExtractionStatus() == ExtractionStatus.FAILED
                || existing.getExtractionStatus() == ExtractionStatus.PENDING)
                && incoming.extractionStatus() == ExtractionStatus.EXTRACTED
                && incoming.contentText() != null
                && !incoming.contentText().isBlank()
                && incoming.extractionError() == null;
    }

    private PublicationDocumentEntity toDocumentEntity(
            PublicationEntity publication,
            PublicationDocumentEvent document
    ) {

        PublicationDocumentEntity entity = new PublicationDocumentEntity();

        entity.setPublication(publication);
        applyDocument(entity, document);
        return entity;
    }

    private void applyDocument(PublicationDocumentEntity entity,
                               PublicationDocumentEvent document) {
        entity.setSourceUrl(document.sourceUrl());
        entity.setContentText(document.contentText());
        entity.setContentHash(document.contentHash());
        entity.setContentLength(document.contentLength());
        entity.setExtractionStatus(document.extractionStatus());
        entity.setExtractionError(document.extractionError());
        entity.setExtractorVersion(document.extractorVersion());
        entity.setExtractedAt(document.extractedAt());
    }

    private PublicationHistoryResponse toHistoryResponse(
            PublicationDocumentEntity document
    ) {

        PublicationEntity publication = document.getPublication();

        return new PublicationHistoryResponse(
                publication.getId(),
                publication.getExternalId(),
                publication.getSource(),
                publication.getTitle(),
                publication.getDocumentType(),
                publication.getPublishedAt(),
                publication.getDownloadUrl(),
                new PublicationDocumentHistoryResponse(
                        document.getContentText(),
                        document.getContentHash(),
                        document.getContentLength(),
                        document.getExtractionStatus(),
                        document.getExtractorVersion(),
                        document.getExtractedAt()
                )
        );
    }

    private boolean isExternalIdUniqueConstraintViolation(
            DataIntegrityViolationException exception
    ) {

        Throwable cause = exception;

        while (cause != null) {
            String message = cause.getMessage();

            if (message != null
                    && message.contains(EXTERNAL_ID_UNIQUE_CONSTRAINT)) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}
