package br.com.fiscalwatch.fiscalservice.publication.entity;

import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin;
import br.com.fiscalwatch.fiscalservice.publication.service.InternalTestPublicationIdentifiers;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "publications")
@Getter
@Setter
@NoArgsConstructor
public class PublicationEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "record_origin", nullable = false, length = 30)
    private PublicationRecordOrigin recordOrigin = PublicationRecordOrigin.PUBLIC;

    @PrePersist
    @PreUpdate
    private void classifyRecordOrigin() {
        if (InternalTestPublicationIdentifiers.matches(source, externalId, downloadUrl)) {
            recordOrigin = PublicationRecordOrigin.INTERNAL_TEST;
        } else if (recordOrigin == null) {
            recordOrigin = PublicationRecordOrigin.PUBLIC;
        }
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 100)
    private DocumentType documentType;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "download_url", columnDefinition = "TEXT")
    private String downloadUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
