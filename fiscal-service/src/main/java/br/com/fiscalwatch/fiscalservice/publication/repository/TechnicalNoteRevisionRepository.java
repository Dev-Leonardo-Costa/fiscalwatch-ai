package br.com.fiscalwatch.fiscalservice.publication.repository;
import br.com.fiscalwatch.fiscalservice.publication.entity.TechnicalNoteRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface TechnicalNoteRevisionRepository extends JpaRepository<TechnicalNoteRevisionEntity, Long> {
    Optional<TechnicalNoteRevisionEntity> findByTechnicalNoteIdAndDeclaredVersionAndContentHashAndSource(
            Long noteId, String version, String hash, String source);
}
