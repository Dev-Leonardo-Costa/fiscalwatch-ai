package br.com.fiscalwatch.fiscalservice.impactanalysis.repository;

import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin;

public interface ImpactAnalysisRepository
        extends JpaRepository<ImpactAnalysis, Long> {
    Optional<ImpactAnalysis> findByDocumentSnapshotIdAndAnalysisVersion(Long snapshotId, String analysisVersion);
    Optional<ImpactAnalysis> findByIdAndPublicationRecordOrigin(Long id, PublicationRecordOrigin recordOrigin);
    List<ImpactAnalysis> findByPublicationIdAndPublicationRecordOrigin(Long publicationId, PublicationRecordOrigin recordOrigin);

    List<ImpactAnalysis> findByPublicationId(Long publicationId);

    Optional<ImpactAnalysis> findFirstByPublicationIdAndAnalysisVersionAndStatusOrderByIdAsc(
            Long publicationId, String analysisVersion, AnalysisStatus status);

    Optional<ImpactAnalysis>
            findFirstByPublicationIdAndAnalysisVersionAndPreviousExternalIdAndComparisonHash(
                    Long publicationId,
                    String analysisVersion,
                    String previousExternalId,
                    String comparisonHash
            );

    boolean existsByPublicationId(Long publicationId);
}
