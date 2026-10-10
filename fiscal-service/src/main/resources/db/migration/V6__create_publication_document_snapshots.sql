CREATE TABLE publication_document_snapshots (
    id BIGSERIAL PRIMARY KEY,
    publication_id BIGINT NOT NULL REFERENCES publications(id) ON DELETE RESTRICT,
    snapshot_hash VARCHAR(64) NOT NULL,
    text_hash VARCHAR(64) NOT NULL,
    content_text TEXT NOT NULL,
    content_length INTEGER NOT NULL,
    source_url TEXT,
    declared_version VARCHAR(100),
    extractor_version VARCHAR(50),
    extracted_at TIMESTAMP,
    captured_at TIMESTAMP,
    original_pdf_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_document_snapshots_identity UNIQUE (publication_id, snapshot_hash),
    CONSTRAINT ck_document_snapshots_content CHECK (content_length > 0 AND length(trim(content_text)) > 0),
    CONSTRAINT ck_document_snapshots_text_hash CHECK (text_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_document_snapshots_identity_hash CHECK (snapshot_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_document_snapshots_pdf_hash CHECK (original_pdf_hash IS NULL OR original_pdf_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_document_snapshots_text_integrity CHECK (
        content_length = char_length(content_text)
        AND text_hash = encode(sha256(convert_to(content_text, 'UTF8')), 'hex')
    )
);

CREATE INDEX idx_document_snapshots_publication ON publication_document_snapshots(publication_id, id);

-- Immutable audit records: prevent even bulk/native UPDATE and DELETE.
CREATE FUNCTION reject_publication_document_snapshot_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Publication document snapshots are immutable'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER publication_document_snapshots_immutable
BEFORE UPDATE OR DELETE ON publication_document_snapshots
FOR EACH ROW EXECUTE FUNCTION reject_publication_document_snapshot_mutation();

CREATE TRIGGER publication_document_snapshots_no_truncate
BEFORE TRUNCATE ON publication_document_snapshots
FOR EACH STATEMENT EXECUTE FUNCTION reject_publication_document_snapshot_mutation();
