package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PublicationDocumentRepositoryTest {

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private PublicationDocumentRepository publicationDocumentRepository;

    @Test
    void devePersistirDocumentoAssociadoAPublicacao() {
        PublicationEntity publication = criarPublicacao();

        PublicationEntity savedPublication =
                publicationRepository.saveAndFlush(publication);

        PublicationDocumentEntity document = new PublicationDocumentEntity();
        document.setPublication(savedPublication);
        document.setSourceUrl("https://example.com/nota-tecnica.pdf");
        document.setContentText("Conteudo extraido da nota tecnica.");
        document.setContentHash("a".repeat(64));
        document.setContentLength(document.getContentText().length());
        document.setExtractionStatus(ExtractionStatus.EXTRACTED);
        document.setExtractorVersion("pypdf-v1");
        document.setExtractedAt(LocalDateTime.now());

        PublicationDocumentEntity savedDocument =
                publicationDocumentRepository.saveAndFlush(document);

        Optional<PublicationDocumentEntity> result =
                publicationDocumentRepository.findByPublicationId(
                        savedPublication.getId()
                );

        assertTrue(result.isPresent());
        assertEquals(savedDocument.getId(), result.get().getId());
        assertEquals(
                savedPublication.getId(),
                result.get().getPublication().getId()
        );
        assertEquals(
                "Conteudo extraido da nota tecnica.",
                result.get().getContentText()
        );
        assertEquals(
                ExtractionStatus.EXTRACTED,
                result.get().getExtractionStatus()
        );
    }

    private PublicationEntity criarPublicacao() {
        PublicationEntity publication = new PublicationEntity();

        publication.setExternalId("a".repeat(64));
        publication.setSource("SVRS");
        publication.setTitle("Nota Técnica 2026.009 v1.00");
        publication.setDocumentType(DocumentType.NOTA_TECNICA);
        publication.setPublishedAt(LocalDateTime.now());
        publication.setDescription("Publicação utilizada no teste");
        publication.setDownloadUrl("https://example.com/nota-tecnica.pdf");

        return publication;
    }
}
