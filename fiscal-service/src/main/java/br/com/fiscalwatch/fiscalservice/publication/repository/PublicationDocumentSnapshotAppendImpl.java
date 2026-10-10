package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentSnapshotEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public class PublicationDocumentSnapshotAppendImpl implements PublicationDocumentSnapshotAppend {
    private final EntityManager entityManager;

    public PublicationDocumentSnapshotAppendImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PublicationDocumentSnapshotEntity append(PublicationDocumentSnapshotEntity snapshot) {
        if (snapshot == null || snapshot.getId() != null) throw new IllegalArgumentException("Somente snapshots novos podem ser anexados");
        Long publicationId = snapshot.getPublication().getId();
        // Serializes concurrent retries without modifying the publication or existing snapshots.
        if (entityManager.find(PublicationEntity.class, publicationId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new IllegalArgumentException("Publicação inexistente");
        }
        var existing = entityManager.createQuery("""
                select snapshot from PublicationDocumentSnapshotEntity snapshot
                where snapshot.publication.id = :publicationId and snapshot.snapshotHash = :hash
                """, PublicationDocumentSnapshotEntity.class)
                .setParameter("publicationId", publicationId).setParameter("hash", snapshot.getSnapshotHash())
                .getResultList();
        if (!existing.isEmpty()) return existing.getFirst();
        entityManager.persist(snapshot);
        entityManager.flush();
        return snapshot;
    }
}
