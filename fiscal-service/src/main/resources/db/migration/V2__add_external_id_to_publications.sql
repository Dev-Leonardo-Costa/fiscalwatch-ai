ALTER TABLE publications
ADD COLUMN external_id VARCHAR(64) NOT NULL;

ALTER TABLE publications
ADD CONSTRAINT uk_publications_external_id
UNIQUE (external_id);