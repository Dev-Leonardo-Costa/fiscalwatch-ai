-- Disposable PostgreSQL only; raise on any regression.
DO $$ BEGIN
 IF (SELECT count(*) FROM publications) <> 4 THEN RAISE EXCEPTION 'Existing data was lost'; END IF;
 IF (SELECT count(*) FROM publications WHERE record_origin='INTERNAL_TEST') <> 3 THEN RAISE EXCEPTION 'Legacy backfill failed'; END IF;
 IF NOT EXISTS (SELECT 1 FROM publications WHERE external_id='official-test-words' AND record_origin='PUBLIC' AND title='NT 2026 TESTE e sintética legítima') THEN RAISE EXCEPTION 'Legitimate title filtered or changed'; END IF;
END $$;
INSERT INTO publications (source, external_id, title, published_at) VALUES
('INTEGRATION_TEST', 'future-sql-fixture', 'Future GTIN', '2026-01-01');
UPDATE publications SET record_origin='PUBLIC' WHERE external_id='future-sql-fixture';
INSERT INTO publications (source, external_id, title, published_at, record_origin) VALUES
('SVRS', 'explicit-fixture', 'GTIN', '2026-01-01', 'INTERNAL_TEST');
DO $$ BEGIN
 IF (SELECT record_origin FROM publications WHERE external_id='future-sql-fixture') <> 'INTERNAL_TEST' THEN RAISE EXCEPTION 'SQL trigger bypass'; END IF;
 IF (SELECT record_origin FROM publications WHERE external_id='explicit-fixture') <> 'INTERNAL_TEST' THEN RAISE EXCEPTION 'Explicit origin lost'; END IF;
 BEGIN
  INSERT INTO publications(source,external_id,title,published_at,record_origin) VALUES ('SVRS','invalid-origin','GTIN','2026-01-01','INVALID');
  RAISE EXCEPTION 'Invalid origin accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
END $$;
