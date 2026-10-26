-- Additive only: legacy publications, documents and analyses are not backfilled.
CREATE TABLE technical_notes (
    id BIGSERIAL PRIMARY KEY,
    family VARCHAR(30) NOT NULL,
    note_year INTEGER NOT NULL CHECK (note_year BETWEEN 2000 AND 2199),
    note_number INTEGER NOT NULL CHECK (note_number > 0),
    CONSTRAINT uk_technical_notes_identity UNIQUE (family, note_year, note_number)
);

-- Keep sources separate, including when their text is equal. Divergent content
-- under the same declared version is another revision, never an UPDATE.
CREATE TABLE technical_note_revisions (
    id BIGSERIAL PRIMARY KEY,
    technical_note_id BIGINT NOT NULL REFERENCES technical_notes(id) ON DELETE RESTRICT,
    declared_version VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    source VARCHAR(50) NOT NULL,
    CONSTRAINT uk_technical_note_revision UNIQUE (technical_note_id, declared_version, content_hash, source)
);

-- Capture metadata belongs to the received document, not the mutable/latest
-- title of a publication whose URL may have been reused.
CREATE TABLE publication_snapshot_receipts (
    snapshot_id BIGINT PRIMARY KEY REFERENCES publication_document_snapshots(id) ON DELETE RESTRICT,
    revision_id BIGINT REFERENCES technical_note_revisions(id) ON DELETE RESTRICT,
    source VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    document_type VARCHAR(100),
    published_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP,
    description TEXT,
    download_url TEXT,
    received_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_snapshot_receipts_revision ON publication_snapshot_receipts(revision_id, snapshot_id);

CREATE TRIGGER technical_notes_immutable BEFORE UPDATE OR DELETE ON technical_notes
FOR EACH ROW EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
CREATE TRIGGER technical_notes_no_truncate BEFORE TRUNCATE ON technical_notes
FOR EACH STATEMENT EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
CREATE TRIGGER technical_note_revisions_immutable BEFORE UPDATE OR DELETE ON technical_note_revisions
FOR EACH ROW EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
CREATE TRIGGER technical_note_revisions_no_truncate BEFORE TRUNCATE ON technical_note_revisions
FOR EACH STATEMENT EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
CREATE TRIGGER snapshot_receipts_immutable BEFORE UPDATE OR DELETE ON publication_snapshot_receipts
FOR EACH ROW EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
CREATE TRIGGER snapshot_receipts_no_truncate BEFORE TRUNCATE ON publication_snapshot_receipts
FOR EACH STATEMENT EXECUTE FUNCTION reject_publication_document_snapshot_mutation();

ALTER TABLE impact_analyses ADD COLUMN document_snapshot_id BIGINT
    REFERENCES publication_document_snapshots(id) ON DELETE RESTRICT;
-- NULL deliberately preserves the unknown provenance of legacy/manual analyses.
CREATE UNIQUE INDEX uk_impact_analysis_snapshot_version
ON impact_analyses(document_snapshot_id, analysis_version)
WHERE document_snapshot_id IS NOT NULL;

-- A snapshot can only be used by its own publication. Protect the binding and
-- completed results from accidental updates/deletion, without freezing legacy rows.
CREATE FUNCTION protect_snapshot_analysis() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.document_snapshot_id IS NOT NULL THEN
            RAISE EXCEPTION 'Snapshot analyses are append-only' USING ERRCODE = '55000';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' AND (OLD.document_snapshot_id IS NOT NULL OR NEW.document_snapshot_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Cannot rewrite analysis provenance or snapshot analysis' USING ERRCODE = '55000';
    END IF;
    IF NEW.document_snapshot_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM publication_document_snapshots s
        WHERE s.id = NEW.document_snapshot_id AND s.publication_id = NEW.publication_id
    ) THEN
        RAISE EXCEPTION 'Snapshot belongs to another publication' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER impact_analysis_snapshot_protection BEFORE INSERT OR UPDATE OR DELETE ON impact_analyses
FOR EACH ROW EXECUTE FUNCTION protect_snapshot_analysis();

CREATE FUNCTION protect_snapshot_analysis_child() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM impact_analyses a WHERE a.id = OLD.impact_analysis_id
            AND a.document_snapshot_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Snapshot analysis results are append-only' USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'UPDATE' AND EXISTS (SELECT 1 FROM impact_analyses a WHERE a.id = NEW.impact_analysis_id
            AND a.document_snapshot_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Cannot attach existing results to snapshot analysis' USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER technical_impacts_snapshot_protection BEFORE UPDATE OR DELETE ON technical_impacts
FOR EACH ROW EXECUTE FUNCTION protect_snapshot_analysis_child();
CREATE TRIGGER evidences_snapshot_protection BEFORE UPDATE OR DELETE ON evidences
FOR EACH ROW EXECUTE FUNCTION protect_snapshot_analysis_child();
CREATE TRIGGER action_items_snapshot_protection BEFORE UPDATE OR DELETE ON action_items
FOR EACH ROW EXECUTE FUNCTION protect_snapshot_analysis_child();

CREATE TRIGGER impact_analyses_no_truncate BEFORE TRUNCATE ON impact_analyses
FOR EACH STATEMENT EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
CREATE TRIGGER technical_impacts_no_truncate BEFORE TRUNCATE ON technical_impacts
FOR EACH STATEMENT EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
CREATE TRIGGER evidences_no_truncate BEFORE TRUNCATE ON evidences
FOR EACH STATEMENT EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
CREATE TRIGGER action_items_no_truncate BEFORE TRUNCATE ON action_items
FOR EACH STATEMENT EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
