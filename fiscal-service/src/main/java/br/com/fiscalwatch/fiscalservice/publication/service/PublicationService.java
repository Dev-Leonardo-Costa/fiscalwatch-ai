package br.com.fiscalwatch.fiscalservice.publication.service;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationDocumentHistoryResponse;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationHistoryResponse;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationResponse;
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

import java.util.List;

@RequiredArgsConstructor
@Service
public class PublicationService {

    private static final String EXTERNAL_ID_UNIQUE_CONSTRAINT =
            "uk_publications_external_id";

    private final PublicationRepository publicationRepository;
    private final PublicationDocumentRepository publicationDocumentRepository;
    private final PublicationMapper publicationMapper;


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
                .findById(id)
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

    @Transactional
    public void processEvent(PublicationEvent event) {

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
