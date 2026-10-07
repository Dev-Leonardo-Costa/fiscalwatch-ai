ALTER TABLE impact_analyses
ADD COLUMN previous_external_id VARCHAR(64),
ADD COLUMN comparison_hash VARCHAR(64);

CREATE UNIQUE INDEX uk_impact_analyses_schema_comparison
ON impact_analyses (
    publication_id,
    analysis_version,
    COALESCE(previous_external_id, ''),
    comparison_hash
)
WHERE comparison_hash IS NOT NULL;
