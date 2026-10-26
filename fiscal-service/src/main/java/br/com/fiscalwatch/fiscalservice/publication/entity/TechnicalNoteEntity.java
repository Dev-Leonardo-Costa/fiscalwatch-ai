package br.com.fiscalwatch.fiscalservice.publication.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Getter
@NoArgsConstructor
@Table(name = "technical_notes", uniqueConstraints = @UniqueConstraint(name = "uk_technical_notes_identity",
        columnNames = {"family", "note_year", "note_number"}))
public class TechnicalNoteEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30) private String family;
    @Column(name = "note_year", nullable = false) private int year;
    @Column(name = "note_number", nullable = false) private int number;

    public TechnicalNoteEntity(String family, int year, int number) {
        this.family = family;
        this.year = year;
        this.number = number;
    }
}
