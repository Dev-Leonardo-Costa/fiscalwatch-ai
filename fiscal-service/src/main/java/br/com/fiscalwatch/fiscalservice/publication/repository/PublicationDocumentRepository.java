package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicationDocumentRepository
        extends JpaRepository<PublicationDocumentEntity, Long> {

    Optional<PublicationDocumentEntity> findByPublicationId(Long publicationId);
}
