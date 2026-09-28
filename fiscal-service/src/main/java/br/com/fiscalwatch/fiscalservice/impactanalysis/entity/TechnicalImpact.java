package br.com.fiscalwatch.fiscalservice.impactanalysis.entity;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "technical_impacts")
@Getter
@Setter
@NoArgsConstructor
public class TechnicalImpact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "impact_analysis_id", nullable = false)
    private ImpactAnalysis impactAnalysis;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "affected_area", length = 100)
    private String affectedArea;

    @Column(name = "affected_component", length = 150)
    private String affectedComponent;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false, length = 50)
    private ImpactLevel impactLevel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
