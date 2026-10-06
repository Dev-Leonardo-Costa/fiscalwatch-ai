package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationHistoryResponse;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationAlreadyExistsException;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationDocumentEvent;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEvent;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Transactional
class PublicationServiceTest {

    @Autowired
    private PublicationService publicationService;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private PublicationDocumentRepository publicationDocumentRepository;

    @Test
    void deveProcessarPublicacaoNovaSemDocumento() {
        PublicationEvent event = criarEvento("new-no-doc", null);

        publicationService.processEvent(event);

        PublicationEntity publication = publicationRepository
                .findByExternalId(event.publication().externalId())
                .orElseThrow();

        assertFalse(
                publicationDocumentRepository
                        .findByPublicationId(publication.getId())
                        .isPresent()
        );
    }

    @Test
    void deveProcessarPublicacaoNovaComDocumento() {
        PublicationDocumentEvent document = criarDocumento(
                ExtractionStatus.EXTRACTED,
                "Conteudo extraido da nota tecnica."
        );
        PublicationEvent event = criarEvento("new-with-doc", document);

        publicationService.processEvent(event);

        PublicationEntity publication = publicationRepository
                .findByExternalId(event.publication().externalId())
                .orElseThrow();
        PublicationDocumentEntity savedDocument = publicationDocumentRepository
                .findByPublicationId(publication.getId())
                .orElseThrow();

        assertEquals(document.sourceUrl(), savedDocument.getSourceUrl());
        assertEquals(document.contentText(), savedDocument.getContentText());
        assertEquals(document.contentHash(), savedDocument.getContentHash());
        assertEquals(document.contentLength(), savedDocument.getContentLength());
        assertEquals(
                document.extractionStatus(),
                savedDocument.getExtractionStatus()
        );
        assertEquals(
                document.extractionError(),
                savedDocument.getExtractionError()
        );
        assertEquals(
                document.extractorVersion(),
                savedDocument.getExtractorVersion()
        );
        assertEquals(document.extractedAt(), savedDocument.getExtractedAt());
    }

    @Test
    void deveCriarDocumentoParaPublicacaoExistenteSemDocumento() {
        PublicationRequest publication = criarPublicacao("existing-no-doc");
        publicationService.create(publication);

        PublicationEvent event = new PublicationEvent(
                "publication.discovered",
                LocalDateTime.now(),
                publication,
                criarDocumento(ExtractionStatus.EXTRACTED, "Documento novo")
        );

        publicationService.processEvent(event);

        PublicationEntity savedPublication = publicationRepository
                .findByExternalId(publication.externalId())
                .orElseThrow();

        assertTrue(
                publicationDocumentRepository
                        .findByPublicationId(savedPublication.getId())
                        .isPresent()
        );
    }

    @Test
    void naoDeveSobrescreverDocumentoExistente() {
        PublicationDocumentEvent originalDocument = criarDocumento(
                ExtractionStatus.EXTRACTED,
                "Conteudo original"
        );
        PublicationEvent originalEvent = criarEvento(
                "existing-with-doc",
                originalDocument
        );

        publicationService.processEvent(originalEvent);

        PublicationDocumentEvent ignoredDocument = criarDocumento(
                ExtractionStatus.FAILED,
                "Conteudo que nao deve sobrescrever"
        );
        PublicationEvent repeatedEvent = new PublicationEvent(
                "publication.discovered",
                LocalDateTime.now(),
                originalEvent.publication(),
                ignoredDocument
        );

        publicationService.processEvent(repeatedEvent);

        PublicationEntity publication = publicationRepository
                .findByExternalId(originalEvent.publication().externalId())
                .orElseThrow();
        PublicationDocumentEntity savedDocument = publicationDocumentRepository
                .findByPublicationId(publication.getId())
                .orElseThrow();

        assertEquals("Conteudo original", savedDocument.getContentText());
        assertEquals(
                ExtractionStatus.EXTRACTED,
                savedDocument.getExtractionStatus()
        );
    }

    @Test
    void devePersistirStatusExtractedCorretamente() {
        PublicationEvent event = criarEvento(
                "status-extracted",
                criarDocumento(ExtractionStatus.EXTRACTED, "Conteudo")
        );

        publicationService.processEvent(event);

        PublicationEntity publication = publicationRepository
                .findByExternalId(event.publication().externalId())
                .orElseThrow();

        assertEquals(
                ExtractionStatus.EXTRACTED,
                publicationDocumentRepository
                        .findByPublicationId(publication.getId())
                        .orElseThrow()
                        .getExtractionStatus()
        );
    }

    @Test
    void devePersistirContentTextCorretamente() {
        String contentText = "Texto integral extraido do documento fiscal.";
        PublicationEvent event = criarEvento(
                "content-text",
                criarDocumento(ExtractionStatus.EXTRACTED, contentText)
        );

        publicationService.processEvent(event);

        PublicationEntity publication = publicationRepository
                .findByExternalId(event.publication().externalId())
                .orElseThrow();

        assertEquals(
                contentText,
                publicationDocumentRepository
                        .findByPublicationId(publication.getId())
                        .orElseThrow()
                        .getContentText()
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deveFazerRollbackDaPublicacaoQuandoDocumentoFalhar() {
        PublicationDocumentEvent invalidDocument = new PublicationDocumentEvent(
                "https://example.com/nota-tecnica.pdf",
                "Conteudo invalido",
                "b".repeat(64),
                17,
                null,
                null,
                "pypdf-v1",
                LocalDateTime.now()
        );
        PublicationEvent event = criarEvento("rollback-doc-fail", invalidDocument);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> publicationService.processEvent(event)
        );

        assertTrue(
                publicationRepository
                        .findByExternalId(event.publication().externalId())
                        .isEmpty()
        );
    }

    @Test
    void deveManterComportamentoRestDeDuplicidade() {
        PublicationRequest publication = criarPublicacao("rest-duplicate");

        publicationService.create(publication);

        assertThrows(
                PublicationAlreadyExistsException.class,
                () -> publicationService.create(publication)
        );
    }

    @Test
    void deveListarHistoricoComDocumentoPorSourceEDocumentType() {
        String source = uniqueSource();
        PublicationEvent schemaEvent = criarEvento(
                "history-schema",
                source,
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 1, 2, 0, 0),
                criarDocumento(ExtractionStatus.EXTRACTED, "Schema extraido")
        );
        criarEventoPersistido(
                "history-nfe",
                "PORTAL_NFE",
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 1, 3, 0, 0),
                criarDocumento(ExtractionStatus.EXTRACTED, "Outro source")
        );
        criarEventoPersistido(
                "history-nt",
                source,
                DocumentType.NOTA_TECNICA,
                LocalDateTime.of(2026, 1, 4, 0, 0),
                criarDocumento(ExtractionStatus.EXTRACTED, "Outro tipo")
        );

        publicationService.processEvent(schemaEvent);

        List<PublicationHistoryResponse> result =
                publicationService.findHistory(source, DocumentType.SCHEMA);

        assertEquals(1, result.size());
        assertEquals(schemaEvent.publication().externalId(),
                result.get(0).externalId());
        assertEquals("Schema extraido", result.get(0).document().contentText());
    }

    @Test
    void deveRetornarSomentePublicacoesComDocumentoNoHistorico() {
        String source = uniqueSource();
        PublicationRequest withoutDocument = criarPublicacao(
                "history-without-doc",
                source,
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
        publicationService.create(withoutDocument);
        PublicationEvent withDocument = criarEvento(
                "history-with-doc",
                source,
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 1, 2, 0, 0),
                criarDocumento(ExtractionStatus.EXTRACTED, "Com documento")
        );

        publicationService.processEvent(withDocument);

        List<PublicationHistoryResponse> result =
                publicationService.findHistory(source, DocumentType.SCHEMA);

        assertEquals(1, result.size());
        assertEquals(withDocument.publication().externalId(),
                result.get(0).externalId());
    }

    @Test
    void deveOrdenarHistoricoPorPublishedAtEId() {
        String source = uniqueSource();
        PublicationEvent secondByDate = criarEvento(
                "history-second-date",
                source,
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 1, 2, 0, 0),
                criarDocumento(ExtractionStatus.EXTRACTED, "Segundo")
        );
        PublicationEvent firstByDate = criarEvento(
                "history-first-date",
                source,
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                criarDocumento(ExtractionStatus.EXTRACTED, "Primeiro")
        );
        PublicationEvent firstById = criarEvento(
                "history-first-id",
                source,
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 1, 3, 0, 0),
                criarDocumento(ExtractionStatus.EXTRACTED, "Id menor")
        );
        PublicationEvent secondById = criarEvento(
                "history-second-id",
                source,
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 1, 3, 0, 0),
                criarDocumento(ExtractionStatus.EXTRACTED, "Id maior")
        );

        publicationService.processEvent(secondByDate);
        publicationService.processEvent(firstByDate);
        publicationService.processEvent(firstById);
        publicationService.processEvent(secondById);

        List<PublicationHistoryResponse> result =
                publicationService.findHistory(source, DocumentType.SCHEMA);

        assertEquals(firstByDate.publication().externalId(),
                result.get(0).externalId());
        assertEquals(secondByDate.publication().externalId(),
                result.get(1).externalId());
        assertEquals(firstById.publication().externalId(),
                result.get(2).externalId());
        assertEquals(secondById.publication().externalId(),
                result.get(3).externalId());
    }

    private PublicationEvent criarEvento(
            String suffix,
            PublicationDocumentEvent document
    ) {
        return criarEvento(
                suffix,
                "SVRS",
                DocumentType.NOTA_TECNICA,
                LocalDateTime.now(),
                document
        );
    }

    private PublicationEvent criarEvento(
            String suffix,
            String source,
            DocumentType documentType,
            LocalDateTime publishedAt,
            PublicationDocumentEvent document
    ) {
        return new PublicationEvent(
                "publication.discovered",
                LocalDateTime.now(),
                criarPublicacao(suffix, source, documentType, publishedAt),
                document
        );
    }

    private PublicationRequest criarPublicacao(String suffix) {
        return criarPublicacao(
                suffix,
                "SVRS",
                DocumentType.NOTA_TECNICA,
                LocalDateTime.now()
        );
    }

    private PublicationRequest criarPublicacao(
            String suffix,
            String source,
            DocumentType documentType,
            LocalDateTime publishedAt
    ) {
        return new PublicationRequest(
                uniqueExternalId(),
                source,
                "Nota Tecnica 2026.009 v1.00",
                documentType,
                publishedAt,
                null,
                "Publicacao utilizada no teste",
                "https://example.com/nota-tecnica.pdf"
        );
    }

    private void criarEventoPersistido(
            String suffix,
            String source,
            DocumentType documentType,
            LocalDateTime publishedAt,
            PublicationDocumentEvent document
    ) {
        publicationService.processEvent(
                criarEvento(
                        suffix,
                        source,
                        documentType,
                        publishedAt,
                        document
                )
        );
    }

    private String uniqueExternalId() {
        return (UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""))
                .substring(0, 64);
    }

    private String uniqueSource() {
        return "SVRS_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12);
    }

    private PublicationDocumentEvent criarDocumento(
            ExtractionStatus status,
            String contentText
    ) {
        return new PublicationDocumentEvent(
                "https://example.com/nota-tecnica.pdf",
                contentText,
                "c".repeat(64),
                contentText.length(),
                status,
                status == ExtractionStatus.FAILED ? "Falha ao extrair" : null,
                "pypdf-v1",
                LocalDateTime.now()
        );
    }
}
