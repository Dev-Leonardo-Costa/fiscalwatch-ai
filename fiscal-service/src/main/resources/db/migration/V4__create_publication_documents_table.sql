CREATE TABLE publication_documents (
    id BIGSERIAL PRIMARY KEY,

    publication_id BIGINT NOT NULL,

    source_url TEXT,
    content_text TEXT,
    content_hash VARCHAR(64),
    content_length INTEGER,

    extraction_status VARCHAR(50) NOT NULL,
    extraction_error TEXT,
    extractor_version VARCHAR(50),
    extracted_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_publication_documents_publication
        FOREIGN KEY (publication_id)
        REFERENCES publications (id)
        ON DELETE CASCADE,

    CONSTRAINT uk_publication_documents_publication_id
        UNIQUE (publication_id)
);

CREATE INDEX idx_publication_documents_extraction_status
    ON publication_documents (extraction_status);
