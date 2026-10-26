package br.com.fiscalwatch.fiscalservice;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** V7 -> V8 upgrade, in a fresh schema inside the explicitly opted-in test cluster. */
@EnabledIfSystemProperty(named = "fiscalwatch.history-postgres-test", matches = "true")
class TechnicalNoteHistoryMigrationPostgresTest {
    @Test void additiveMigrationPreservesLegacyDocumentsAnalysesAndEvidenceWithoutInventingBindings() {
        String schema = "history_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://127.0.0.1:55449/fiscalwatch_history_test?currentSchema=" + schema;
        var config = Flyway.configure().dataSource(url, "history_test", "").schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration");
        config.target("7").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "history_test", ""));
        Long publication = jdbc.queryForObject("""
                insert into publications(source, title, document_type, published_at, external_id, download_url)
                values ('SVRS', 'NT 2025.002 v1.51', 'NOTA_TECNICA', timestamp '2025-01-01 00:00:00',
                        'legacy-upgrade', 'https://fixtures.invalid/NFE/legacy.pdf') returning id
                """, Long.class);
        jdbc.update("""
                insert into publication_documents(publication_id, content_text, content_hash, content_length, extraction_status)
                values (?, 'Legacy document', ?, 15, 'EXTRACTED')
                """, publication, br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher.calculateContentHash("Legacy document"));
        Long analysis = jdbc.queryForObject("""
                insert into impact_analyses(publication_id, summary, impact_level, status, analysis_version, production_deadline)
                values (?, 'Legacy summary', 'HIGH', 'COMPLETED', 'automatic-v1', timestamp '2025-03-01 00:00:00') returning id
                """, Long.class, publication);
        jdbc.update("insert into evidences(impact_analysis_id, excerpt) values (?, 'Legacy evidence')", analysis);
        var oldPublication = jdbc.queryForMap("select * from publications where id = ?", publication);
        var oldDocument = jdbc.queryForMap("select * from publication_documents where publication_id = ?", publication);
        var oldAnalysis = jdbc.queryForMap("select * from impact_analyses where id = ?", analysis);
        var oldEvidence = jdbc.queryForMap("select * from evidences where impact_analysis_id = ?", analysis);

        Flyway.configure().dataSource(url, "history_test", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();

        assertEquals(oldPublication, jdbc.queryForMap("select * from publications where id = ?", publication));
        assertEquals(oldDocument, jdbc.queryForMap("select * from publication_documents where publication_id = ?", publication));
        var migratedAnalysis = jdbc.queryForMap("select * from impact_analyses where id = ?", analysis);
        assertTrue(migratedAnalysis.containsKey("document_snapshot_id"));
        assertNull(migratedAnalysis.remove("document_snapshot_id"));
        assertEquals(oldAnalysis, migratedAnalysis);
        assertEquals(oldEvidence, jdbc.queryForMap("select * from evidences where impact_analysis_id = ?", analysis));
        assertEquals(0, jdbc.queryForObject("select count(*) from publication_document_snapshots", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from technical_notes", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from publication_snapshot_receipts", Integer.class));
    }
}
