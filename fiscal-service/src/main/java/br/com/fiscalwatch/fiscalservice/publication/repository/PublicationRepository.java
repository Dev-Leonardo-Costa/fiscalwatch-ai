package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicationRepository extends JpaRepository<PublicationEntity, Long> {
    boolean existsByExternalId(String externalId);
}