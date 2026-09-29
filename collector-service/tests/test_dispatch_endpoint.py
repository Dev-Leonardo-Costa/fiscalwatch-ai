from fastapi.testclient import TestClient

import main


client = TestClient(main.app)


def test_endpoint_dispatch_unitario_retorna_200(monkeypatch):
    external_id = "a" * 64

    monkeypatch.setattr(
        main,
        "dispatch_svrs_publication_by_external_id",
        lambda requested_external_id: {
            "source": "SVRS",
            "status": "PUBLISHED",
            "published": 1,
            "publication": {
                "external_id": requested_external_id,
                "title": "Nota Técnica 2026.009 v1.00",
                "download_url": "https://example.com/nota.pdf"
            },
            "document": {
                "extraction_status": "EXTRACTED",
                "content_length": 12345,
                "content_hash": "abc123",
                "extraction_error": None,
                "extractor_version": "svrs-pypdf-v1",
                "extracted_at": "2026-09-29T10:00:00"
            }
        }
    )

    response = client.post(
        f"/dispatch/svrs/publications/{external_id}"
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "PUBLISHED"
    assert payload["published"] == 1
    assert payload["publication"]["external_id"] == external_id
    assert payload["document"]["extraction_status"] == "EXTRACTED"
    assert "content_text" not in payload["document"]


def test_endpoint_dispatch_unitario_retorna_404_quando_nao_encontrado(
    monkeypatch
):
    external_id = "b" * 64

    monkeypatch.setattr(
        main,
        "dispatch_svrs_publication_by_external_id",
        lambda requested_external_id: {
            "source": "SVRS",
            "status": "NOT_FOUND",
            "published": 0,
            "external_id": requested_external_id
        }
    )

    response = client.post(
        f"/dispatch/svrs/publications/{external_id}"
    )

    assert response.status_code == 404
    assert response.json()["detail"] == {
        "source": "SVRS",
        "status": "NOT_FOUND",
        "published": 0,
        "external_id": external_id
    }
