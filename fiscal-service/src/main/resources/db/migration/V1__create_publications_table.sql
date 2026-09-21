CREATE TABLE publications (
    id BIGSERIAL PRIMARY KEY,

    source VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    document_type VARCHAR(100),

    published_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP,

    description TEXT,
    download_url TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);