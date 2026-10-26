package br.com.fiscalwatch.fiscalservice.publication.entity;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import java.time.LocalDateTime;

@Entity
@Immutable
@Getter
@NoArgsConstructor
@Table(name = "publication_snapshot_receipts")
public class PublicationSnapshotReceiptEntity {
    @Id @Column(name = "snapshot_id") private Long snapshotId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id") private TechnicalNoteRevisionEntity revision;
    @Column(nullable = false, length = 50) private String source;
    @Column(nullable = false, length = 500) private String title;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", length = 100) private DocumentType documentType;
    @Column(name = "published_at", nullable = false) private LocalDateTime publishedAt;
    @Column(name = "modified_at") private LocalDateTime modifiedAt;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "download_url", columnDefinition = "TEXT") private String downloadUrl;
    @Column(name = "received_at", nullable = false) private LocalDateTime receivedAt;

    public PublicationSnapshotReceiptEntity(Long snapshotId, TechnicalNoteRevisionEntity revision,
            PublicationRequest request, LocalDateTime receivedAt) {
        this.snapshotId = snapshotId;
        this.revision = revision;
        this.source = request.source();
        this.title = request.title();
        this.documentType = request.documentType();
        this.publishedAt = request.publishedAt();
        this.modifiedAt = request.modifiedAt();
        this.description = request.description();
        this.downloadUrl = request.downloadUrl();
        this.receivedAt = receivedAt;
    }
}
