package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;

import java.util.List;
import java.util.Optional;

public interface PublicationDocumentRepository
        extends JpaRepository<PublicationDocumentEntity, Long> {

    Optional<PublicationDocumentEntity> findByPublicationId(Long publicationId);

    @Transactional(readOnly = true)
    @Query("""
            select document from PublicationDocumentEntity document
            join fetch document.publication publication
            where publication.source = 'SVRS'
              and publication.id > :afterId
              and document.extractionStatus = :extracted
              and document.extractionError is null
              and document.contentText is not null
              and length(trim(document.contentText)) > 0
              and document.contentHash is not null
              and length(document.contentHash) = 64
              and document.contentLength > 0
              and not exists (
                  select analysis.id from ImpactAnalysis analysis
                  where analysis.publication.id = publication.id
                    and analysis.analysisVersion = :version
                    and analysis.status = :completed
              )
            order by publication.id asc
            """)
    List<PublicationDocumentEntity> findPendingSvrsAnalysisDocuments(
            @Param("afterId") long afterId,
            @Param("extracted") ExtractionStatus extracted,
            @Param("version") String version,
            @Param("completed") AnalysisStatus completed,
            Pageable pageable);

    @Query("""
            select document
            from PublicationDocumentEntity document
            join fetch document.publication publication
            where publication.source = :source
              and publication.documentType = :documentType
            order by publication.publishedAt asc, publication.id asc
            """)
    List<PublicationDocumentEntity> findHistoryByPublication(
            @Param("source") String source,
            @Param("documentType") DocumentType documentType
    );
}
