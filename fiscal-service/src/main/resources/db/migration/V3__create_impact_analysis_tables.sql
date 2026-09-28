CREATE TABLE impact_analyses (
    id BIGSERIAL PRIMARY KEY,

    publication_id BIGINT NOT NULL,

    summary TEXT,
    impact_level VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    analysis_version VARCHAR(50) NOT NULL,

    homologation_deadline TIMESTAMP,
    production_deadline TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    analyzed_at TIMESTAMP,

    CONSTRAINT fk_impact_analyses_publication
        FOREIGN KEY (publication_id)
        REFERENCES publications (id)
        ON DELETE RESTRICT
);

CREATE TABLE technical_impacts (
    id BIGSERIAL PRIMARY KEY,

    impact_analysis_id BIGINT NOT NULL,

    title VARCHAR(200) NOT NULL,
    description TEXT,
    affected_area VARCHAR(100),
    affected_component VARCHAR(150),
    impact_level VARCHAR(50) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_technical_impacts_impact_analysis
        FOREIGN KEY (impact_analysis_id)
        REFERENCES impact_analyses (id)
        ON DELETE CASCADE
);

CREATE TABLE action_items (
    id BIGSERIAL PRIMARY KEY,

    impact_analysis_id BIGINT NOT NULL,

    title VARCHAR(200) NOT NULL,
    description TEXT,
    target_professional_profile VARCHAR(100) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    due_at TIMESTAMP,
    completed BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_action_items_impact_analysis
        FOREIGN KEY (impact_analysis_id)
        REFERENCES impact_analyses (id)
        ON DELETE CASCADE
);

CREATE TABLE evidences (
    id BIGSERIAL PRIMARY KEY,

    impact_analysis_id BIGINT NOT NULL,

    source_title VARCHAR(500),
    source_url TEXT,
    document_section VARCHAR(200),
    page_number INTEGER,
    excerpt TEXT NOT NULL,
    start_position INTEGER,
    end_position INTEGER,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_evidences_impact_analysis
        FOREIGN KEY (impact_analysis_id)
        REFERENCES impact_analyses (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_impact_analyses_publication_id
    ON impact_analyses (publication_id);

CREATE INDEX idx_technical_impacts_impact_analysis_id
    ON technical_impacts (impact_analysis_id);

CREATE INDEX idx_action_items_impact_analysis_id
    ON action_items (impact_analysis_id);

CREATE INDEX idx_evidences_impact_analysis_id
    ON evidences (impact_analysis_id);
