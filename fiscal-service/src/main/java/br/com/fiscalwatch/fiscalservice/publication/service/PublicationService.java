package br.com.fiscalwatch.fiscalservice.publication.service;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationResponse;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationNotFoundException;
import br.com.fiscalwatch.fiscalservice.publication.mapper.PublicationMapper;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final PublicationMapper publicationMapper;


    @Transactional
    public PublicationResponse create(PublicationRequest request) {

        PublicationEntity entity = publicationMapper.toEntity(request);

        PublicationEntity savedEntity = publicationRepository.save(entity);

        return publicationMapper.toResponse(savedEntity);
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
}