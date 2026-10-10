package br.com.fiscalwatch.fiscalservice.publication.entity;

import br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

/** Append-only extracted document, independent of the legacy publication_documents record. */
@Entity
@Immutable
@Getter
@Table(name = "publication_document_snapshots", uniqueConstraints =
        @UniqueConstraint(name = "uk_document_snapshots_identity", columnNames = {"publication_id", "snapshot_hash"}))
public class PublicationDocumentSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false, updatable = false)
    private PublicationEntity publication;
    @Column(name = "snapshot_hash", nullable = false, length = 64, updatable = false)
    private String snapshotHash;
    @Column(name = "text_hash", nullable = false, length = 64, updatable = false)
    private String textHash;
    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String contentText;
    @Column(name = "content_length", nullable = false, updatable = false)
    private Integer contentLength;
    @Column(name = "source_url", columnDefinition = "TEXT", updatable = false)
    private String sourceUrl;
    @Column(name = "declared_version", length = 100, updatable = false)
    private String declaredVersion;
    @Column(name = "extractor_version", length = 50, updatable = false)
    private String extractorVersion;
    @Column(name = "extracted_at", updatable = false)
    private LocalDateTime extractedAt;
    @Column(name = "captured_at", updatable = false)
    private LocalDateTime capturedAt;
    @Column(name = "original_pdf_hash", length = 64, updatable = false)
    private String originalPdfHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PublicationDocumentSnapshotEntity() { }

    public static PublicationDocumentSnapshotEntity create(PublicationEntity publication, String text,
            String sourceUrl, String declaredVersion, String extractorVersion,
            LocalDateTime extractedAt, LocalDateTime capturedAt, String originalPdfHash) {
        var snapshot = new PublicationDocumentSnapshotEntity();
        snapshot.publication = Objects.requireNonNull(publication, "Publicação obrigatória");
        if (publication.getId() == null) throw new IllegalArgumentException("Publicação deve estar persistida");
        snapshot.contentText = Objects.requireNonNull(text, "Texto obrigatório");
        if (text.isBlank()) throw new IllegalArgumentException("Texto vazio não constitui snapshot documental");
        snapshot.textHash = PublicationSnapshotHasher.calculateContentHash(text);
        snapshot.contentLength = text.codePointCount(0, text.length());
        snapshot.sourceUrl = sourceUrl;
        snapshot.declaredVersion = declaredVersion;
        snapshot.extractorVersion = extractorVersion;
        snapshot.extractedAt = extractedAt;
        snapshot.capturedAt = capturedAt;
        snapshot.originalPdfHash = originalPdfHash;
        snapshot.snapshotHash = snapshot.calculateIdentity();
        snapshot.validate();
        return snapshot;
    }

    // Distinct namespace from the experimental publication-metadata snapshot hash.
    // Length-prefixed UTF-8 values preserve null/empty and avoid concatenation collisions.
    private String calculateIdentity() {
        StringBuilder canonical = new StringBuilder("fiscalwatch.document-snapshot:v1\n");
        for (String value : new String[]{textHash, sourceUrl, declaredVersion, extractorVersion, originalPdfHash}) {
            canonical.append(value == null ? "-1:" : value.getBytes(StandardCharsets.UTF_8).length + ":" + value);
            canonical.append('\n');
        }
        return PublicationSnapshotHasher.calculateContentHash(canonical.toString());
    }

    @PrePersist
    private void beforeInsert() {
        validate();
        createdAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }

    private void validate() {
        if (publication == null || publication.getId() == null || contentText == null || contentText.isBlank()
                || !Objects.equals(textHash, PublicationSnapshotHasher.calculateContentHash(contentText))
                || !Objects.equals(contentLength, contentText.codePointCount(0, contentText.length()))
                || !Objects.equals(snapshotHash, calculateIdentity())
                || (declaredVersion != null && declaredVersion.length() > 100)
                || (extractorVersion != null && extractorVersion.length() > 50)
                || (originalPdfHash != null && !originalPdfHash.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("Snapshot documental inválido");
        }
    }

    @PreUpdate
    @PreRemove
    private void rejectMutation() {
        throw new IllegalStateException("Snapshots documentais são imutáveis");
    }
}
