package br.com.fiscalwatch.fiscalservice.publication.repository;
import br.com.fiscalwatch.fiscalservice.publication.entity.TechnicalNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface TechnicalNoteRepository extends JpaRepository<TechnicalNoteEntity, Long> {
    Optional<TechnicalNoteEntity> findByFamilyAndYearAndNumber(String family, int year, int number);
}
