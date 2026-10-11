-- Disposable PostgreSQL only; run after V1-V6, before V7.
INSERT INTO publications (source, external_id, title, published_at, download_url) VALUES
('SVRS', 'official-test-words', 'NT 2026 TESTE e sintética legítima', '2026-01-01', 'https://dfe-portal.svrs.rs.gov.br/nota.pdf'),
('INTEGRATION_TEST', 'legacy-source-fixture', 'GTIN', '2026-01-01', NULL),
('SVRS', 'it-svrs-job-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-valid', 'GTIN', '2026-01-01', NULL),
('SVRS', 'legacy-url-fixture', 'GTIN', '2026-01-01', 'https://fixtures.invalid/svrs/mvp-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.pdf');
