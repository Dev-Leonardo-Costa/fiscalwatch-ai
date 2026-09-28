package br.com.fiscalwatch.fiscalservice.publication.entity;

import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "publication_documents")
@Getter
@Setter
@NoArgsConstructor
public class PublicationDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private PublicationEntity publication;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "content_length")
    private Integer contentLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 50)
    private ExtractionStatus extractionStatus;

    @Column(name = "extraction_error", columnDefinition = "TEXT")
    private String extractionError;

    @Column(name = "extractor_version", length = 50)
    private String extractorVersion;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
