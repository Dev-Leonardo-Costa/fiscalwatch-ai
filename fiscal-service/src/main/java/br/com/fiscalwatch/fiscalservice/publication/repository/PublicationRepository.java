package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PublicationRepository extends JpaRepository<PublicationEntity, Long> {
    boolean existsByExternalId(String externalId);

    Optional<PublicationEntity> findByExternalId(String externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select publication from PublicationEntity publication "
            + "where publication.externalId = :externalId")
    Optional<PublicationEntity> findByExternalIdForUpdate(
            @Param("externalId") String externalId);

}
