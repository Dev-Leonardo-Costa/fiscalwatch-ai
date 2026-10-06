package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PublicationDocumentRepository
        extends JpaRepository<PublicationDocumentEntity, Long> {

    Optional<PublicationDocumentEntity> findByPublicationId(Long publicationId);

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
