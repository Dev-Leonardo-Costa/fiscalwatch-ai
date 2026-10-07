package br.com.fiscalwatch.fiscalservice.impactanalysis.repository;

import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImpactAnalysisRepository
        extends JpaRepository<ImpactAnalysis, Long> {

    List<ImpactAnalysis> findByPublicationId(Long publicationId);

    Optional<ImpactAnalysis>
            findFirstByPublicationIdAndAnalysisVersionAndPreviousExternalIdAndComparisonHash(
                    Long publicationId,
                    String analysisVersion,
                    String previousExternalId,
                    String comparisonHash
            );

    boolean existsByPublicationId(Long publicationId);
}
