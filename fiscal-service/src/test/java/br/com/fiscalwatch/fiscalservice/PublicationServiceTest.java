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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.persistence.EntityManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private EntityManager entityManager;

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

    @ParameterizedTest
    @EnumSource(value = ExtractionStatus.class, names = {"FAILED", "PENDING"})
    void deveRecuperarDocumentoSemDuplicarOuAlterarRelacionamento(ExtractionStatus status) {
        PublicationEvent inicial = criarEvento("recuperacao", criarDocumento(status, ""));
        publicationService.processEvent(inicial);
        PublicationDocumentEntity anterior = documentoDe(inicial);
        Long documentoId = anterior.getId();
        Long publicacaoId = anterior.getPublication().getId();
        LocalDateTime criadoEm = anterior.getCreatedAt();
        PublicationDocumentEvent extraido = criarDocumento(ExtractionStatus.EXTRACTED,
                "Conteudo recuperado com sucesso");

        publicationService.processEvent(comDocumento(inicial, extraido));
        entityManager.clear();
        PublicationDocumentEntity recuperado = documentoDe(inicial);

        assertEquals(documentoId, recuperado.getId());
        assertEquals(publicacaoId, recuperado.getPublication().getId());
        assertEquals(criadoEm, recuperado.getCreatedAt());
        assertDocumento(extraido, recuperado);
        assertUnicoDocumento(publicacaoId);
    }

    @Test
    void deveManterEventoExtraidoDuplicadoIdempotente() {
        PublicationEvent evento = criarEvento("duplicado", criarDocumento(
                ExtractionStatus.EXTRACTED, "Conteudo original"));
        publicationService.processEvent(evento);
        PublicationDocumentEntity inicial = documentoDe(evento);
        Long documentoId = inicial.getId();
        LocalDateTime atualizadoEm = inicial.getUpdatedAt();

        publicationService.processEvent(evento);
        entityManager.clear();
        PublicationDocumentEntity preservado = documentoDe(evento);

        assertEquals(documentoId, preservado.getId());
        assertEquals(atualizadoEm, preservado.getUpdatedAt());
        assertDocumento(evento.document(), preservado);
        assertUnicoDocumento(preservado.getPublication().getId());
    }

    @ParameterizedTest
    @EnumSource(ExtractionStatus.class)
    void devePreservarDocumentoValidoContraEventosPosteriores(ExtractionStatus status) {
        PublicationEvent inicial = criarEvento("preservar", criarDocumento(
                ExtractionStatus.EXTRACTED, "Conteudo valido original"));
        publicationService.processEvent(inicial);
        Long documentoId = documentoDe(inicial).getId();

        publicationService.processEvent(comDocumento(inicial,
                criarDocumento(status, "Conteudo diferente recebido depois")));
        entityManager.clear();
        PublicationDocumentEntity preservado = documentoDe(inicial);

        assertEquals(documentoId, preservado.getId());
        assertDocumento(inicial.document(), preservado);
        assertUnicoDocumento(preservado.getPublication().getId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\n\t"})
    void naoDeveRecuperarFailedComExtractedSemTextoValido(String texto) {
        PublicationEvent inicial = criarEvento("texto-invalido",
                criarDocumento(ExtractionStatus.FAILED, ""));
        publicationService.processEvent(inicial);
        PublicationDocumentEvent invalido = new PublicationDocumentEvent(
                "https://example.com/documento.pdf", texto, null, null,
                ExtractionStatus.EXTRACTED, null, "extrator-v2", LocalDateTime.now());

        publicationService.processEvent(comDocumento(inicial, invalido));
        entityManager.clear();

        assertDocumento(inicial.document(), documentoDe(inicial));
    }

    @Test
    void naoDeveRecuperarFailedComExtractedQueAindaInformaErro() {
        PublicationEvent inicial = criarEvento("erro-inconsistente",
                criarDocumento(ExtractionStatus.FAILED, ""));
        publicationService.processEvent(inicial);
        PublicationDocumentEvent inconsistente = new PublicationDocumentEvent(
                "https://example.com/documento.pdf", "Texto parcial", null, 13,
                ExtractionStatus.EXTRACTED, "Extracao interrompida", "v2", LocalDateTime.now());

        publicationService.processEvent(comDocumento(inicial, inconsistente));
        entityManager.clear();

        assertDocumento(inicial.document(), documentoDe(inicial));
    }

    @Test
    void deveRecuperarMesmoComEventoDeSucessoMaisAntigoESemRegredirDepois() {
        PublicationEvent falha = criarEvento("fora-de-ordem",
                criarDocumento(ExtractionStatus.FAILED, ""));
        publicationService.processEvent(falha);
        PublicationDocumentEvent extraido = new PublicationDocumentEvent(
                "https://example.com/documento.pdf", "Conteudo recuperado", "a".repeat(64),
                19, ExtractionStatus.EXTRACTED, null, "extrator-v2",
                falha.document().extractedAt().minusDays(1));
        PublicationEvent sucessoAntigo = new PublicationEvent("publication.discovered",
                falha.occurredAt().minusDays(1), falha.publication(), extraido);

        publicationService.processEvent(sucessoAntigo);
        publicationService.processEvent(falha);
        entityManager.clear();

        assertDocumento(extraido, documentoDe(falha));
        assertUnicoDocumento(documentoDe(falha).getPublication().getId());
    }

    @Test
    void naoDeveAlterarEmptyNemMetadadosDaPublicacao() {
        PublicationEvent inicial = criarEvento("empty-preservado",
                criarDocumento(ExtractionStatus.EMPTY, ""));
        publicationService.processEvent(inicial);
        PublicationDocumentEvent extraido = criarDocumento(ExtractionStatus.EXTRACTED,
                "Novo conteudo fora do escopo de recuperacao");

        publicationService.processEvent(comDocumento(inicial, extraido));
        entityManager.clear();

        assertDocumento(inicial.document(), documentoDe(inicial));
        assertEquals(inicial.publication().title(), documentoDe(inicial).getPublication().getTitle());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deveSerializarEventosConcorrentesDaPublicacaoExistente(boolean semDocumento)
            throws Exception {
        PublicationEvent inicial = criarEvento("concorrente", semDocumento ? null
                : criarDocumento(ExtractionStatus.FAILED, ""));
        publicationService.processEvent(inicial);
        Long publicacaoId = publicationRepository.findByExternalId(
                inicial.publication().externalId()).orElseThrow().getId();
        Long documentoId = publicationDocumentRepository.findByPublicationId(publicacaoId)
                .map(PublicationDocumentEntity::getId).orElse(null);
        PublicationEvent sucesso = comDocumento(inicial,
                criarDocumento(ExtractionStatus.EXTRACTED, "Conteudo concorrente"));
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch prontos = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        try {
            var tarefa = (java.util.concurrent.Callable<Void>) () -> {
                prontos.countDown();
                if (!iniciar.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Prazo para iniciar teste excedido");
                }
                publicationService.processEvent(sucesso);
                return null;
            };
            var primeiro = executor.submit(tarefa);
            var segundo = executor.submit(tarefa);
            assertTrue(prontos.await(10, TimeUnit.SECONDS));
            iniciar.countDown();
            primeiro.get(20, TimeUnit.SECONDS);
            segundo.get(20, TimeUnit.SECONDS);

            PublicationDocumentEntity salvo = publicationDocumentRepository
                    .findByPublicationId(publicacaoId).orElseThrow();
            if (documentoId != null) assertEquals(documentoId, salvo.getId());
            assertEquals(publicacaoId, salvo.getPublication().getId());
            assertDocumento(sucesso.document(), salvo);
            assertUnicoDocumento(publicacaoId);
        } finally {
            iniciar.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
            // Remove somente a fixture exclusiva que este teste confirmou.
            publicationDocumentRepository.findByPublicationId(publicacaoId)
                    .ifPresent(publicationDocumentRepository::delete);
            publicationRepository.deleteById(publicacaoId);
        }
    }

    private PublicationEvent comDocumento(PublicationEvent evento,
                                          PublicationDocumentEvent documento) {
        return new PublicationEvent(evento.eventType(), LocalDateTime.now(),
                evento.publication(), documento);
    }

    private PublicationDocumentEntity documentoDe(PublicationEvent evento) {
        Long publicacaoId = publicationRepository.findByExternalId(
                evento.publication().externalId()).orElseThrow().getId();
        return publicationDocumentRepository.findByPublicationId(publicacaoId).orElseThrow();
    }

    private void assertUnicoDocumento(Long publicacaoId) {
        assertEquals(1L, entityManager.createQuery(
                "select count(document) from PublicationDocumentEntity document "
                        + "where document.publication.id = :id", Long.class)
                .setParameter("id", publicacaoId).getSingleResult());
    }

    private void assertDocumento(PublicationDocumentEvent esperado,
                                 PublicationDocumentEntity salvo) {
        assertEquals(esperado.sourceUrl(), salvo.getSourceUrl());
        assertEquals(esperado.contentText(), salvo.getContentText());
        assertEquals(esperado.contentHash(), salvo.getContentHash());
        assertEquals(esperado.contentLength(), salvo.getContentLength());
        assertEquals(esperado.extractionStatus(), salvo.getExtractionStatus());
        assertEquals(esperado.extractionError(), salvo.getExtractionError());
        assertEquals(esperado.extractorVersion(), salvo.getExtractorVersion());
        assertEquals(esperado.extractedAt(), salvo.getExtractedAt());
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
                // PostgreSQL persiste timestamps com precisão de microssegundos.
                LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
        );
    }
}
