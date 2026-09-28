package br.com.fiscalwatch.fiscalservice.impactanalysis.repository;

import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImpactAnalysisRepository
        extends JpaRepository<ImpactAnalysis, Long> {

    List<ImpactAnalysis> findByPublicationId(Long publicationId);

    boolean existsByPublicationId(Long publicationId);
}
