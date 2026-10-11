-- Add explicit provenance without deleting publications, documents, snapshots or analyses.
ALTER TABLE publications
ADD COLUMN record_origin VARCHAR(30) NOT NULL DEFAULT 'PUBLIC';

ALTER TABLE publications ADD CONSTRAINT ck_publications_record_origin
CHECK (record_origin IN ('PUBLIC', 'INTERNAL_TEST'));

-- Backfill only proven fixture namespaces; do not match words in titles/descriptions.
UPDATE publications SET record_origin = 'INTERNAL_TEST'
WHERE upper(source) = 'INTEGRATION_TEST'
   OR (upper(source) = 'SVRS' AND (
       external_id ~ '^it-svrs-job-[0-9a-f]{32}-(valid|failed)$'
       OR download_url ~ '^https://fixtures[.]invalid/svrs/mvp-[0-9a-f]{32}[.]pdf$'
   ));

-- Apply the same explicit classification to future SQL inserts as well as JPA ingestion.
CREATE FUNCTION classify_publication_record_origin()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF upper(NEW.source) = 'INTEGRATION_TEST'
       OR (upper(NEW.source) = 'SVRS' AND (
           NEW.external_id ~ '^it-svrs-job-[0-9a-f]{32}-(valid|failed)$'
           OR NEW.download_url ~ '^https://fixtures[.]invalid/svrs/mvp-[0-9a-f]{32}[.]pdf$'
       )) THEN
        NEW.record_origin := 'INTERNAL_TEST';
    ELSE
        NEW.record_origin := COALESCE(NEW.record_origin, 'PUBLIC');
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER publications_classify_record_origin
BEFORE INSERT OR UPDATE OF source, external_id, download_url, record_origin ON publications
FOR EACH ROW EXECUTE FUNCTION classify_publication_record_origin();
