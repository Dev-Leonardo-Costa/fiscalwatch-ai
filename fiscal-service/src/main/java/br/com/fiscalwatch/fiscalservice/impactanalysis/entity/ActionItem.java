package br.com.fiscalwatch.fiscalservice.impactanalysis.entity;

import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_items")
@Getter
@Setter
@NoArgsConstructor
public class ActionItem {

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

    @Column(name = "target_professional_profile", nullable = false, length = 100)
    private String targetProfessionalProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 50)
    private ImpactLevel priority;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "completed", nullable = false)
    private Boolean completed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
