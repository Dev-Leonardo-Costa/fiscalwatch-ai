-- Run only on a disposable PostgreSQL database with V1, V2 and V6 applied.
-- Transaction rollback leaves no test records even on the disposable database.
BEGIN;
DO $$
DECLARE
    publication_id_test BIGINT;
    original_text TEXT := 'Original ' || chr(129534) || E'\r\n';
    original_hash TEXT;
BEGIN
    original_hash := encode(sha256(convert_to(original_text, 'UTF8')), 'hex');
    INSERT INTO publications(external_id, source, title, published_at)
    VALUES (repeat('a', 64), 'TEST', 'Disposable snapshot test', CURRENT_TIMESTAMP)
    RETURNING id INTO publication_id_test;

    INSERT INTO publication_document_snapshots(publication_id, snapshot_hash, text_hash,
        content_text, content_length, source_url, declared_version)
    VALUES (publication_id_test, repeat('a', 64), original_hash, original_text,
        char_length(original_text), 'https://example.com/same.pdf', '1.21');
    INSERT INTO publication_document_snapshots(publication_id, snapshot_hash, text_hash,
        content_text, content_length, source_url, declared_version)
    VALUES (publication_id_test, repeat('b', 64), encode(sha256(convert_to('Changed', 'UTF8')), 'hex'),
        'Changed', 7, 'https://example.com/same.pdf', '1.21');

    BEGIN
        INSERT INTO publication_document_snapshots(publication_id, snapshot_hash, text_hash, content_text, content_length)
        VALUES (publication_id_test, repeat('a', 64), original_hash, original_text, char_length(original_text));
        RAISE EXCEPTION 'Expected identity unique constraint rejection';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO publication_document_snapshots(publication_id, snapshot_hash, text_hash, content_text, content_length)
        VALUES (publication_id_test, repeat('c', 64), repeat('e', 64), original_text, char_length(original_text));
        RAISE EXCEPTION 'Expected incorrect text hash rejection';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO publication_document_snapshots(publication_id, snapshot_hash, text_hash, content_text, content_length)
        VALUES (publication_id_test, repeat('c', 64), original_hash, original_text, 1);
        RAISE EXCEPTION 'Expected incorrect code point length rejection';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO publication_document_snapshots(publication_id, snapshot_hash, text_hash, content_text, content_length, original_pdf_hash)
        VALUES (publication_id_test, repeat('c', 64), original_hash, original_text, char_length(original_text), 'invalid');
        RAISE EXCEPTION 'Expected malformed PDF hash rejection';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO publication_document_snapshots(publication_id, snapshot_hash, text_hash, content_text, content_length)
        VALUES (-1, repeat('c', 64), original_hash, original_text, char_length(original_text));
        RAISE EXCEPTION 'Expected snapshot foreign key rejection';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;

    BEGIN
        UPDATE publication_document_snapshots SET content_text = 'overwritten' WHERE publication_id = publication_id_test;
        RAISE EXCEPTION 'Expected native UPDATE rejection';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;

    BEGIN
        DELETE FROM publication_document_snapshots WHERE publication_id = publication_id_test;
        RAISE EXCEPTION 'Expected native DELETE rejection';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;

    BEGIN
        TRUNCATE publication_document_snapshots;
        RAISE EXCEPTION 'Expected TRUNCATE rejection';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;

    BEGIN
        DELETE FROM publications WHERE id = publication_id_test;
        RAISE EXCEPTION 'Expected publication deletion restriction';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;

    IF (SELECT count(*) FROM publication_document_snapshots WHERE publication_id = publication_id_test) <> 2
        OR (SELECT content_text FROM publication_document_snapshots WHERE snapshot_hash = repeat('a', 64)) <> original_text THEN
        RAISE EXCEPTION 'Snapshots were modified or distinct content was lost';
    END IF;
END;
$$;
ROLLBACK;
