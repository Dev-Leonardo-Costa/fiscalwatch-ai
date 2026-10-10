package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentSnapshotEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentSnapshotRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:document-snapshot-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PublicationDocumentSnapshotRepositoryTest {
    @Autowired PublicationRepository publications;
    @Autowired PublicationDocumentSnapshotRepository snapshots;
    @Autowired EntityManager entityManager;

    @Test
    void preservesExactUnicodeTextAndAvailableMetadataWithoutInventingPdfHash() {
        var publication = publication();
        String text = "  Versão 1.21\r\nGTIN 🧾 e\u0301\n";
        var saved = snapshots.append(snapshot(publication, text, "1.21", "pypdf-v1", null));
        entityManager.clear();
        var loaded = snapshots.findById(saved.getId()).orElseThrow();
        assertEquals(text, loaded.getContentText());
        assertEquals(text.codePointCount(0, text.length()), loaded.getContentLength());
        assertEquals(PublicationSnapshotHasher.calculateContentHash(text), loaded.getTextHash());
        assertEquals("https://example.com/nt.pdf", loaded.getSourceUrl());
        assertEquals("1.21", loaded.getDeclaredVersion());
        assertEquals("pypdf-v1", loaded.getExtractorVersion());
        assertEquals(LocalDateTime.of(2026, 10, 9, 12, 0), loaded.getExtractedAt());
        assertNull(loaded.getCapturedAt());
        assertNull(loaded.getOriginalPdfHash());
        assertNotNull(loaded.getCreatedAt());
        assertEquals(publication.getId(), loaded.getPublication().getId());
    }

    @Test
    void preservesDifferentContentsAtSameUrlAndDeclaredVersion() {
        var publication = publication();
        var first = snapshots.append(snapshot(publication, "GTIN original", "1.21", "v1", null));
        var second = snapshots.append(snapshot(publication, "GTIN corrigido", "1.21", "v1", null));
        assertNotEquals(first.getId(), second.getId());
        assertNotEquals(first.getSnapshotHash(), second.getSnapshotHash());
        assertEquals(2, snapshots.findByPublicationIdOrderByIdAsc(publication.getId()).size());
    }

    @Test
    void retriesReturnExistingSnapshotWithoutOverwritingCaptureMetadata() {
        var publication = publication();
        var first = snapshots.append(snapshot(publication, "GTIN", null, null, null));
        var retry = PublicationDocumentSnapshotEntity.create(publication, "GTIN", "https://example.com/nt.pdf",
                null, null, LocalDateTime.of(2026, 10, 10, 12, 0), LocalDateTime.of(2026, 10, 10, 10, 0), null);
        var result = snapshots.append(retry);
        assertEquals(first.getId(), result.getId());
        assertEquals(first.getExtractedAt(), result.getExtractedAt());
        assertNull(result.getCapturedAt());
        assertEquals(1, snapshots.findByPublicationIdOrderByIdAsc(publication.getId()).size());
    }

    @Test
    void metadataAndPublicationIdentityDoNotCollapseDistinctSnapshots() {
        var publication = publication();
        snapshots.append(snapshot(publication, "GTIN", "1.21", "v1", null));
        snapshots.append(snapshot(publication, "GTIN", "1.21", "v2", null));
        snapshots.append(snapshot(publication, "GTIN", "1.22", "v1", null));
        snapshots.append(PublicationDocumentSnapshotEntity.create(publication, "GTIN", "https://example.com/other.pdf",
                "1.21", "v1", null, null, null));
        snapshots.append(snapshot(publication(), "GTIN", "1.21", "v1", null));
        assertEquals(4, snapshots.findByPublicationIdOrderByIdAsc(publication.getId()).size());
    }

    @Test
    void originalPdfHashIsExplicitSeparateMetadata() {
        var snapshot = snapshots.append(snapshot(publication(), "GTIN", null, null, "b".repeat(64)));
        assertEquals("b".repeat(64), snapshot.getOriginalPdfHash());
        assertNotEquals(snapshot.getTextHash(), snapshot.getOriginalPdfHash());
    }

    @Test
    void uniqueConstraintRejectsBypassingAppendWithDuplicateIdentity() {
        var publication = publication();
        snapshots.append(snapshot(publication, "GTIN", "1.21", "v1", null));
        assertThrows(RuntimeException.class, () -> {
            entityManager.persist(snapshot(publication, "GTIN", "1.21", "v1", null));
            entityManager.flush();
        });
    }

    @Test
    void immutableEntityDoesNotOverwriteTextEvenWhenFieldIsChangedReflectively() throws Exception {
        var saved = snapshots.append(snapshot(publication(), "GTIN original", null, null, null));
        var field = PublicationDocumentSnapshotEntity.class.getDeclaredField("contentText");
        field.setAccessible(true);
        field.set(saved, "alterado");
        entityManager.flush();
        entityManager.clear();
        assertEquals("GTIN original", snapshots.findById(saved.getId()).orElseThrow().getContentText());
    }

    @Test
    void disallowsReappendingPersistedEntity() {
        var saved = snapshots.append(snapshot(publication(), "GTIN", null, null, null));
        var exception = assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                () -> snapshots.append(saved));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void disallowsDeletion() {
        var saved = snapshots.append(snapshot(publication(), "GTIN", null, null, null));
        assertThrows(IllegalStateException.class, () -> entityManager.remove(saved));
    }

    @Test
    void revalidatesContentIntegrityBeforeInsert() throws Exception {
        var candidate = snapshot(publication(), "GTIN", null, null, null);
        var field = PublicationDocumentSnapshotEntity.class.getDeclaredField("textHash");
        field.setAccessible(true);
        field.set(candidate, "a".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> entityManager.persist(candidate));
    }

    @Test
    void rejectsEmptyTextInvalidUnicodeAndMalformedBinaryHash() {
        var publication = publication();
        assertThrows(IllegalArgumentException.class, () -> snapshot(publication, " \n", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> snapshot(publication, "\uD800", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> snapshot(publication, "GTIN", null, null, "not-a-hash"));
    }

    private PublicationEntity publication() {
        var publication = new PublicationEntity();
        publication.setExternalId(PublicationSnapshotHasher.calculateContentHash(UUID.randomUUID().toString()));
        publication.setSource("SVRS");
        publication.setTitle("Publicação de teste");
        publication.setPublishedAt(LocalDateTime.of(2026, 10, 9, 0, 0));
        return publications.saveAndFlush(publication);
    }

    private PublicationDocumentSnapshotEntity snapshot(PublicationEntity publication, String text,
            String version, String extractor, String binaryHash) {
        return PublicationDocumentSnapshotEntity.create(publication, text, "https://example.com/nt.pdf", version,
                extractor, LocalDateTime.of(2026, 10, 9, 12, 0), null, binaryHash);
    }
}
