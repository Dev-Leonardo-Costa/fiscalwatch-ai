package br.com.fiscalwatch.fiscalservice.impactanalysis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidences")
@Getter
@Setter
@NoArgsConstructor
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "impact_analysis_id", nullable = false)
    private ImpactAnalysis impactAnalysis;

    @Column(name = "source_title", length = 500)
    private String sourceTitle;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "document_section", length = 200)
    private String documentSection;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "excerpt", nullable = false, columnDefinition = "TEXT")
    private String excerpt;

    @Column(name = "start_position")
    private Integer startPosition;

    @Column(name = "end_position")
    private Integer endPosition;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
