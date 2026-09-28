package br.com.fiscalwatch.fiscalservice.publication.service;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationResponse;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationAlreadyExistsException;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationNotFoundException;
import br.com.fiscalwatch.fiscalservice.publication.mapper.PublicationMapper;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PublicationService {

    private static final String EXTERNAL_ID_UNIQUE_CONSTRAINT =
            "uk_publications_external_id";

    private final PublicationRepository publicationRepository;
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
