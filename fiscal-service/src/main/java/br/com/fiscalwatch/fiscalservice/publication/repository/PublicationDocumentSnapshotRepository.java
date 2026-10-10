package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentSnapshotEntity;
import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.Optional;

/** Deliberately excludes save/merge, delete and bulk mutation operations. */
public interface PublicationDocumentSnapshotRepository
        extends Repository<PublicationDocumentSnapshotEntity, Long>, PublicationDocumentSnapshotAppend {
    Optional<PublicationDocumentSnapshotEntity> findById(Long id);
    Optional<PublicationDocumentSnapshotEntity> findByPublicationIdAndSnapshotHash(Long publicationId, String snapshotHash);
    List<PublicationDocumentSnapshotEntity> findByPublicationIdOrderByIdAsc(Long publicationId);
}
