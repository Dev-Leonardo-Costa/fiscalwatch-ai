package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin;

public interface PublicationRepository extends JpaRepository<PublicationEntity, Long> {
    @Query("""
            select publication from PublicationEntity publication
            where publication.recordOrigin = br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin.PUBLIC
              and (lower(publication.title) like :pattern escape '!'
               or lower(coalesce(publication.description, '')) like :pattern escape '!'
               or cast(extract(year from publication.publishedAt) as string) like :pattern escape '!')
            order by publication.publishedAt desc, publication.id desc
            """)
    List<PublicationEntity> search(@Param("pattern") String pattern, Pageable pageable);
    Optional<PublicationEntity> findByIdAndRecordOrigin(Long id, PublicationRecordOrigin recordOrigin);
    boolean existsByIdAndRecordOrigin(Long id, PublicationRecordOrigin recordOrigin);

    boolean existsByExternalId(String externalId);

    Optional<PublicationEntity> findByExternalId(String externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select publication from PublicationEntity publication where publication.id = :id")
    Optional<PublicationEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select publication from PublicationEntity publication "
            + "where publication.externalId = :externalId")
    Optional<PublicationEntity> findByExternalIdForUpdate(
            @Param("externalId") String externalId);

}
