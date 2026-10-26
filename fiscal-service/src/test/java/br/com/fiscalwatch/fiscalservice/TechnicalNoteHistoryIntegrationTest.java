package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.*;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.*;
import br.com.fiscalwatch.fiscalservice.impactanalysis.repository.ImpactAnalysisRepository;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.ImpactAnalysisService;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.entity.*;
import br.com.fiscalwatch.fiscalservice.publication.enums.*;
import br.com.fiscalwatch.fiscalservice.publication.messaging.*;
import br.com.fiscalwatch.fiscalservice.publication.repository.*;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import br.com.fiscalwatch.fiscalservice.publication.versioning.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No network, jobs or external database. Production SQL triggers are tested separately. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:nt-history;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "fiscalwatch.impact-analysis-job.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"
})
class TechnicalNoteHistoryIntegrationTest {
    @Autowired PublicationService ingestion;
    @Autowired ImpactAnalysisService service;
    @Autowired PublicationRepository publications;
    @Autowired PublicationDocumentRepository documents;
    @Autowired PublicationDocumentSnapshotRepository snapshots;
    @Autowired ImpactAnalysisRepository analyses;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean ImpactAnalyzer analyzer;

    @BeforeEach void isolate() {
        assertTrue(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection ->
                isolatedDatabase(connection.getMetaData().getURL())));
        when(analyzer.analyze(any())).thenAnswer(call -> {
            PublicationAnalysisInput input = call.getArgument(0);
            String text = input.document().contentText();
            return new ImpactAnalysisResult(input.title() + " | " + text, ImpactLevel.MEDIUM, null, null,
                    List.of(new TechnicalImpactResult("Regra", text, "Fiscal", "NF-e", ImpactLevel.MEDIUM)), List.of(),
                    List.of(new EvidenceResult(input.title(), input.document().sourceUrl(), "Regra", 1, text, 0, text.length())));
        });
    }

    protected boolean isolatedDatabase(String url) {
        return url.startsWith("jdbc:h2:mem:nt-history");
    }

    private PublicationEvent event(String id, String source, String version, String suffix) {
        String url = "https://fixtures.invalid/NFE/" + id + ".pdf";
        String text = "Nota Técnica 2025.002 v" + version + "\nRegra de validação " + suffix;
        var request = new PublicationRequest(id, source, "NT 2025.002 v" + version,
                DocumentType.NOTA_TECNICA, LocalDateTime.of(2025, 1, 1, 0, 0), null, null, url);
        var document = new PublicationDocumentEvent(url, text, PublicationSnapshotHasher.calculateContentHash(text),
                text.codePointCount(0, text.length()), ExtractionStatus.EXTRACTED, null, "test-pdf-v1", LocalDateTime.now());
        return new PublicationEvent("publication.discovered", LocalDateTime.now(), request, document);
    }

    private Long ingest(PublicationEvent event) {
        ingestion.processEvent(event);
        return publications.findByExternalId(event.publication().externalId()).orElseThrow().getId();
    }

    @Test void reusedUrlPreserves151And152AndIndependentAnalyses() {
        String id = UUID.randomUUID().toString();
        var firstEvent = event(id, "SVRS", "1.51", "primeira");
        Long publicationId = ingest(firstEvent);
        var first = service.findById(service.analyzePublication(publicationId).id());
        Long legacyDocumentId = documents.findByPublicationId(publicationId).orElseThrow().getId();
        ingest(event(id, "SVRS", "1.52", "segunda"));
        var second = service.analyzePublication(publicationId);
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.documentSnapshotId(), second.documentSnapshotId());
        var history = ingestion.findSnapshots(publicationId);
        assertEquals(List.of("1.51", "1.52"), history.stream().map(h -> h.declaredVersion()).toList());
        assertEquals("NFE_NFCE", history.getFirst().family());
        assertEquals(2025, history.getFirst().year());
        assertEquals(2, history.getFirst().number());
        assertNotEquals(history.getFirst().revisionId(), history.getLast().revisionId());
        assertEquals(first, service.findById(first.id()));
        assertEquals(first.id(), service.analyzeSnapshot(publicationId, first.documentSnapshotId()).id());
        assertEquals(second.id(), service.analyzeSnapshot(publicationId, second.documentSnapshotId()).id());
        assertTrue(second.summary().contains("v1.52"));
        assertTrue(first.evidences().getFirst().excerpt().contains("primeira"));
        assertEquals(legacyDocumentId, documents.findByPublicationId(publicationId).orElseThrow().getId());
        assertEquals(firstEvent.document().contentText(), documents.findByPublicationId(publicationId).orElseThrow().getContentText());
        verify(analyzer, times(2)).analyze(any());
    }

    @Test void twoVersionsReceivedBeforeAnalysisRemainIndividuallyAddressable() {
        String id = UUID.randomUUID().toString();
        Long publicationId = ingest(event(id, "PORTAL_NFE", "1.51", "antes"));
        ingest(event(id, "PORTAL_NFE", "1.52", "depois"));
        var history = ingestion.findSnapshots(publicationId);
        var old = service.analyzeSnapshot(publicationId, history.getFirst().snapshotId());
        var current = service.analyzePublication(publicationId);
        assertNotEquals(old.id(), current.id());
        assertEquals(history.getLast().snapshotId(), current.documentSnapshotId());
    }

    @Test void sameDeclaredVersionWithDifferentContentCreatesRevisionAndRedeliveryDoesNot() {
        String id = UUID.randomUUID().toString();
        var original = event(id, "SVRS", "1.51", "original");
        Long publicationId = ingest(original);
        var revised = event(id, "SVRS", "1.51", "corrigida");
        ingest(revised);
        ingest(revised);
        ingest(original); // Out-of-order redelivery does not promote an older snapshot.
        var history = ingestion.findSnapshots(publicationId);
        assertEquals(2, history.size());
        assertNotEquals(history.getFirst().revisionId(), history.getLast().revisionId());
        assertNotEquals(history.getFirst().contentHash(), history.getLast().contentHash());
        assertEquals(history.getLast().snapshotId(), service.analyzePublication(publicationId).documentSnapshotId());
    }

    @Test void extractorChangeCreatesAnotherSnapshotOfTheSameFiscalRevision() {
        var original = event(UUID.randomUUID().toString(), "SVRS", "1.51", "same text");
        Long id = ingest(original);
        var d = original.document();
        ingest(new PublicationEvent(original.eventType(), LocalDateTime.now(), original.publication(),
                new PublicationDocumentEvent(d.sourceUrl(), d.contentText(), d.contentHash(), d.contentLength(),
                        d.extractionStatus(), null, "test-pdf-v2", LocalDateTime.now())));
        var history = ingestion.findSnapshots(id);
        assertEquals(2, history.size());
        assertEquals(history.getFirst().revisionId(), history.getLast().revisionId());
    }

    @Test void reusedUrlAndStaleTitleUseTheVersionOfTheReceivedDocumentForAnalysis() {
        String token = UUID.randomUUID().toString();
        var old = event(token, "SVRS", "1.51", "old");
        Long id = ingest(old);
        var received = event(token, "SVRS", "1.52", "received");
        ingest(new PublicationEvent(received.eventType(), received.occurredAt(), old.publication(), received.document()));
        var response = service.analyzePublication(id);
        assertTrue(response.summary().startsWith("Nota Técnica 2025.002 v1.52 |"));
        assertEquals("1.52", ingestion.findSnapshots(id).getLast().declaredVersion());
    }

    @Test void malformedExtractionNeverBecomesAnAuditedSnapshotOrChangesThePriorDocument() {
        var original = event(UUID.randomUUID().toString(), "SVRS", "1.51", "valid");
        Long id = ingest(original);
        var d = original.document();
        ingest(new PublicationEvent(original.eventType(), LocalDateTime.now(), original.publication(),
                new PublicationDocumentEvent(d.sourceUrl(), "corrupt", "0".repeat(64), 7,
                        ExtractionStatus.EXTRACTED, null, d.extractorVersion(), LocalDateTime.now())));
        assertEquals(1, ingestion.findSnapshots(id).size());
        assertEquals(d.contentText(), documents.findByPublicationId(id).orElseThrow().getContentText());
    }

    @Test void sourceDivergenceIsNeverMergedAndMatchingContentRetainsProvenance() {
        String token = UUID.randomUUID().toString();
        Long portalId = ingest(event(token + "p", "PORTAL_NFE", "1.52", token));
        Long svrsId = ingest(event(token + "s", "SVRS", "1.52", token + " divergente"));
        var portal = ingestion.findSnapshots(portalId).getFirst();
        var svrs = ingestion.findSnapshots(svrsId).getFirst();
        assertNotEquals(portal.revisionId(), svrs.revisionId());
        assertNotEquals(portal.contentHash(), svrs.contentHash());
        assertEquals(portal.family(), svrs.family());
        Long mirrorId = ingest(event(token + "m", "SVRS", "1.52", token));
        var mirror = ingestion.findSnapshots(mirrorId).getFirst();
        assertEquals(portal.contentHash(), mirror.contentHash());
        assertNotEquals(portal.revisionId(), mirror.revisionId());
        assertEquals("PORTAL_NFE", portal.source());
        assertEquals("SVRS", mirror.source());
        assertNotEquals(service.analyzePublication(portalId).id(), service.analyzePublication(svrsId).id());
    }

    @Test void concurrentFirstDeliveriesAndAnalysisAreIdempotent() throws Exception {
        String id = UUID.randomUUID().toString();
        var event = event(id, "SVRS", "1.51", id);
        concurrently(() -> ingest(event), 4);
        Long publicationId = publications.findByExternalId(id).orElseThrow().getId();
        assertEquals(1, ingestion.findSnapshots(publicationId).size());
        List<ImpactAnalysisResponse> results = concurrently(() -> service.analyzePublication(publicationId), 4);
        assertEquals(1, results.stream().map(ImpactAnalysisResponse::id).distinct().count());
        assertEquals(1, analyses.findByPublicationId(publicationId).size());
        verify(analyzer, times(1)).analyze(any());
    }

    @Test void concurrentCanonicalCreationAcrossUrlsDoesNotMergeSnapshots() throws Exception {
        String token = UUID.randomUUID().toString();
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        // Unique note number makes this test exercise canonical INSERT contention every time.
        var results = concurrently(() -> {
            var original = event(token + counter.incrementAndGet(), "SVRS", "1.52", token);
            String text = original.document().contentText().replace("2025.002", "2025.987");
            var request = original.publication();
            var changed = new PublicationRequest(request.externalId(), request.source(), "NT 2025.987 v1.52",
                    request.documentType(), request.publishedAt(), null, null, request.downloadUrl());
            return ingest(new PublicationEvent(original.eventType(), original.occurredAt(), changed,
                    new PublicationDocumentEvent(request.downloadUrl(), text, PublicationSnapshotHasher.calculateContentHash(text),
                            text.length(), ExtractionStatus.EXTRACTED, null, "test-pdf-v1", LocalDateTime.now())));
        }, 3);
        var history = results.stream().map(p -> ingestion.findSnapshots(p).getFirst()).toList();
        assertEquals(3, history.stream().map(h -> h.snapshotId()).distinct().count());
        assertEquals(1, history.stream().map(h -> h.revisionId()).distinct().count());
    }

    @Test void legacyAnalysisIsNeverRetroactivelyBound() {
        String id = UUID.randomUUID().toString();
        var firstEvent = event(id, "SVRS", "1.51", "antiga");
        Long publicationId = ingestion.create(firstEvent.publication()).id();
        var legacy = new ImpactAnalysis();
        legacy.setPublication(publications.findById(publicationId).orElseThrow());
        legacy.setStatus(AnalysisStatus.COMPLETED);
        legacy.setImpactLevel(ImpactLevel.LOW);
        legacy.setAnalysisVersion("automatic-v1");
        legacy.setSummary("Resultado legado intocado");
        Long legacyId = analyses.saveAndFlush(legacy).getId();
        assertEquals(legacyId, service.analyzePublication(publicationId).id());
        ingest(firstEvent);
        var newAnalysis = service.analyzePublication(publicationId);
        assertNotEquals(legacyId, newAnalysis.id());
        assertNotNull(newAnalysis.documentSnapshotId());
        assertNull(service.findById(legacyId).documentSnapshotId());
        assertEquals("Resultado legado intocado", service.findById(legacyId).summary());
    }

    @Test void cannotAnalyzeAnotherPublicationsSnapshot() {
        Long first = ingest(event(UUID.randomUUID().toString(), "SVRS", "1.51", "a"));
        Long second = ingest(event(UUID.randomUUID().toString(), "SVRS", "1.52", "b"));
        Long snapshot = ingestion.findSnapshots(first).getFirst().snapshotId();
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.analyzeSnapshot(second, snapshot));
        verifyNoInteractions(analyzer);
    }

    @Test void failedAnalysisRollsBackWithoutChangingHistoryAndCanRetry() {
        Long id = ingest(event(UUID.randomUUID().toString(), "SVRS", "1.51", "falha"));
        var before = ingestion.findSnapshots(id);
        doThrow(new IllegalStateException("Test failure")).when(analyzer).analyze(any());
        assertThrows(IllegalStateException.class, () -> service.analyzePublication(id));
        assertTrue(analyses.findByPublicationId(id).isEmpty());
        assertEquals(before, ingestion.findSnapshots(id));
        doReturn(new ImpactAnalysisResult("Retry", ImpactLevel.LOW,
                null, null, List.of(), List.of(), List.of())).when(analyzer).analyze(any());
        assertNotNull(service.analyzePublication(id).documentSnapshotId());
    }

    @Test void databaseRejectsDuplicateSnapshotAnalyzerButAllowsAnotherAnalyzerVersion() {
        Long id = ingest(event(UUID.randomUUID().toString(), "SVRS", "1.51", "unique"));
        var response = service.analyzePublication(id);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                        analyses.saveAndFlush(copyAnalysis(id, response.documentSnapshotId(), "automatic-v1"))));
        var next = analyses.saveAndFlush(copyAnalysis(id, response.documentSnapshotId(), "automatic-v2"));
        assertNotNull(next.getId());
        assertEquals(2, analyses.findByPublicationId(id).size());
    }

    private ImpactAnalysis copyAnalysis(Long id, Long snapshotId, String analyzerVersion) {
        var analysis = new ImpactAnalysis();
        analysis.setPublication(publications.findById(id).orElseThrow());
        analysis.setDocumentSnapshot(snapshots.findById(snapshotId).orElseThrow());
        analysis.setAnalysisVersion(analyzerVersion);
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setImpactLevel(ImpactLevel.LOW);
        return analysis;
    }

    private <T> List<T> concurrently(Callable<T> action, int threads) throws Exception {
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        try {
            var tasks = new ArrayList<Future<T>>();
            for (int i = 0; i < threads; i++) tasks.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return action.call();
            }));
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            var results = new ArrayList<T>();
            for (var task : tasks) results.add(task.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }
}
