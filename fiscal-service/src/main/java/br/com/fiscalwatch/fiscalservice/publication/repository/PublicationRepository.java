package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicationRepository extends JpaRepository<PublicationEntity, Long> {
    boolean existsByExternalId(String externalId);

    Optional<PublicationEntity> findByExternalId(String externalId);
}
