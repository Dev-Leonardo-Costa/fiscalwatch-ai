package br.com.fiscalwatch.fiscalservice.impactanalysis.entity;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "impact_analyses")
@Getter
@Setter
@NoArgsConstructor
public class ImpactAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private PublicationEntity publication;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false, length = 50)
    private ImpactLevel impactLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AnalysisStatus status;

    @Column(name = "analysis_version", nullable = false, length = 50)
    private String analysisVersion;

    @Column(name = "homologation_deadline")
    private LocalDateTime homologationDeadline;

    @Column(name = "production_deadline")
    private LocalDateTime productionDeadline;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @OneToMany(
            mappedBy = "impactAnalysis",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<TechnicalImpact> technicalImpacts = new ArrayList<>();

    @OneToMany(
            mappedBy = "impactAnalysis",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ActionItem> actionItems = new ArrayList<>();

    @OneToMany(
            mappedBy = "impactAnalysis",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Evidence> evidences = new ArrayList<>();

    public void addTechnicalImpact(TechnicalImpact technicalImpact) {
        technicalImpacts.add(technicalImpact);
        technicalImpact.setImpactAnalysis(this);
    }

    public void removeTechnicalImpact(TechnicalImpact technicalImpact) {
        technicalImpacts.remove(technicalImpact);
        technicalImpact.setImpactAnalysis(null);
    }

    public void addActionItem(ActionItem actionItem) {
        actionItems.add(actionItem);
        actionItem.setImpactAnalysis(this);
    }

    public void removeActionItem(ActionItem actionItem) {
        actionItems.remove(actionItem);
        actionItem.setImpactAnalysis(null);
    }

    public void addEvidence(Evidence evidence) {
        evidences.add(evidence);
        evidence.setImpactAnalysis(this);
    }

    public void removeEvidence(Evidence evidence) {
        evidences.remove(evidence);
        evidence.setImpactAnalysis(null);
    }
}
