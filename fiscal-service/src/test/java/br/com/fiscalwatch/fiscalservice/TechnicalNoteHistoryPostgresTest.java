package br.com.fiscalwatch.fiscalservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, loopback-only throwaway cluster. Never falls back to application credentials. */
@EnabledIfSystemProperty(named = "fiscalwatch.history-postgres-test", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55449/fiscalwatch_history_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=history_test", "spring.datasource.password=",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "fiscalwatch.impact-analysis-job.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"
})
class TechnicalNoteHistoryPostgresTest extends TechnicalNoteHistoryIntegrationTest {
    @Override protected boolean isolatedDatabase(String url) {
        return url.equals("jdbc:postgresql://127.0.0.1:55449/fiscalwatch_history_test");
    }

    @Test void postgresRejectsMutationOfSnapshotBoundResultsAndProvenance() {
        var event = eventForSqlTest();
        ingestion.processEvent(event);
        Long id = publications.findByExternalId(event.publication().externalId()).orElseThrow().getId();
        var response = service.analyzePublication(id);
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                jdbc.update("update impact_analyses set summary = 'changed' where id = ?", response.id()));
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                jdbc.update("delete from impact_analyses where id = ?", response.id()));
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                jdbc.update("update evidences set excerpt = 'changed' where impact_analysis_id = ?", response.id()));
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                jdbc.update("delete from technical_impacts where impact_analysis_id = ?", response.id()));
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                jdbc.update("delete from publication_snapshot_receipts where snapshot_id = ?", response.documentSnapshotId()));
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                jdbc.update("update publication_document_snapshots set content_text = 'changed' where id = ?", response.documentSnapshotId()));
        var other = eventForSqlTest();
        ingestion.processEvent(other);
        Long otherId = publications.findByExternalId(other.publication().externalId()).orElseThrow().getId();
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("""
                insert into impact_analyses(publication_id, document_snapshot_id, impact_level, status, analysis_version)
                values (?, ?, 'LOW', 'COMPLETED', 'invalid-cross-publication')
                """, otherId, response.documentSnapshotId()));
    }

    private br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEvent eventForSqlTest() {
        String id = java.util.UUID.randomUUID().toString();
        String text = "NT 2025.002 v1.52\nRegra auditável " + id;
        var request = new br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest(id, "PORTAL_NFE",
                "NT 2025.002 v1.52", br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType.NOTA_TECNICA,
                java.time.LocalDateTime.now(), null, null, "https://fixtures.invalid/NFE/" + id + ".pdf");
        var document = new br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationDocumentEvent(request.downloadUrl(), text,
                br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher.calculateContentHash(text), text.length(),
                br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus.EXTRACTED, null, "test", java.time.LocalDateTime.now());
        return new br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEvent("publication.discovered",
                java.time.LocalDateTime.now(), request, document);
    }
}
