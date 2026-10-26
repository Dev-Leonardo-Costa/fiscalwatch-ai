package br.com.fiscalwatch.fiscalservice.publication.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Getter
@NoArgsConstructor
@Table(name = "technical_note_revisions", uniqueConstraints = @UniqueConstraint(name = "uk_technical_note_revision",
        columnNames = {"technical_note_id", "declared_version", "content_hash", "source"}))
public class TechnicalNoteRevisionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technical_note_id", nullable = false) private TechnicalNoteEntity technicalNote;
    @Column(name = "declared_version", nullable = false, length = 100) private String declaredVersion;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
    @Column(nullable = false, length = 50) private String source;

    public TechnicalNoteRevisionEntity(TechnicalNoteEntity note, String version, String hash, String source) {
        this.technicalNote = note;
        this.declaredVersion = version;
        this.contentHash = hash;
        this.source = source;
    }
}
