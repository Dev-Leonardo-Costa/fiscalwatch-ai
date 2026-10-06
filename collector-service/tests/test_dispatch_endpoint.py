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


def test_endpoint_dispatch_receita_retorna_200(monkeypatch):
    monkeypatch.setattr(
        main,
        "dispatch_receita_publications",
        lambda: {
            "source": "RECEITA_FEDERAL",
            "collected": 2,
            "published": 2
        },
        raising=False
    )

    response = client.post("/dispatch/receita")

    assert response.status_code == 200
    assert response.json() == {
        "source": "RECEITA_FEDERAL",
        "collected": 2,
        "published": 2
    }


def test_endpoint_dispatch_receita_unitario_retorna_200(monkeypatch):
    external_id = "c" * 64

    monkeypatch.setattr(
        main,
        "dispatch_receita_publication_by_external_id",
        lambda requested_external_id: {
            "source": "RECEITA_FEDERAL",
            "status": "PUBLISHED",
            "published": 1,
            "publication": {
                "external_id": requested_external_id,
                "title": "Receita Federal publica documentação técnica",
                "download_url": (
                    "https://www.gov.br/receitafederal/noticia"
                )
            },
            "document": {
                "extraction_status": "EXTRACTED",
                "content_length": 12345,
                "content_hash": "abc123",
                "extraction_error": None,
                "extractor_version": "receita-html-v1",
                "extracted_at": "2026-09-29T10:00:00"
            }
        },
        raising=False
    )

    response = client.post(
        f"/dispatch/receita/publications/{external_id}"
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "RECEITA_FEDERAL"
    assert payload["status"] == "PUBLISHED"
    assert payload["published"] == 1
    assert payload["publication"]["external_id"] == external_id
    assert payload["document"]["extraction_status"] == "EXTRACTED"
    assert "content_text" not in payload["document"]


def test_endpoint_dispatch_receita_unitario_retorna_404_quando_nao_encontrado(
    monkeypatch
):
    external_id = "d" * 64

    monkeypatch.setattr(
        main,
        "dispatch_receita_publication_by_external_id",
        lambda requested_external_id: {
            "source": "RECEITA_FEDERAL",
            "status": "NOT_FOUND",
            "published": 0,
            "external_id": requested_external_id
        },
        raising=False
    )

    response = client.post(
        f"/dispatch/receita/publications/{external_id}"
    )

    assert response.status_code == 404
    assert response.json()["detail"] == {
        "source": "RECEITA_FEDERAL",
        "status": "NOT_FOUND",
        "published": 0,
        "external_id": external_id
    }


def test_endpoint_dispatch_cgibs_retorna_200(monkeypatch):
    monkeypatch.setattr(
        main,
        "dispatch_cgibs_publications",
        lambda: {
            "source": "CGIBS",
            "collected": 1,
            "published": 1
        },
        raising=False
    )

    response = client.post("/dispatch/cgibs")

    assert response.status_code == 200
    assert response.json() == {
        "source": "CGIBS",
        "collected": 1,
        "published": 1
    }


def test_endpoint_dispatch_cgibs_unitario_retorna_200(monkeypatch):
    external_id = "e" * 64

    monkeypatch.setattr(
        main,
        "dispatch_cgibs_publication_by_external_id",
        lambda requested_external_id: {
            "source": "CGIBS",
            "status": "PUBLISHED",
            "published": 1,
            "publication": {
                "external_id": requested_external_id,
                "title": "Declaração de Regimes Específicos (DeRE)",
                "download_url": (
                    "https://www.cgibs.gov.br/"
                    "declaracao-de-regimes-especificos-dere"
                )
            },
            "document": {
                "extraction_status": "EXTRACTED",
                "content_length": 69119,
                "content_hash": "hash-cgibs",
                "extraction_error": None,
                "extractor_version": "cgibs-pypdf-v1",
                "extracted_at": "2026-10-05T19:01:20"
            }
        },
        raising=False
    )

    response = client.post(
        f"/dispatch/cgibs/publications/{external_id}"
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "CGIBS"
    assert payload["status"] == "PUBLISHED"
    assert payload["published"] == 1
    assert payload["publication"]["external_id"] == external_id
    assert payload["document"]["extraction_status"] == "EXTRACTED"
    assert payload["document"]["extractor_version"] == "cgibs-pypdf-v1"
    assert "content_text" not in payload["document"]


def test_endpoint_dispatch_cgibs_unitario_retorna_404_quando_nao_encontrado(
    monkeypatch
):
    external_id = "f" * 64

    monkeypatch.setattr(
        main,
        "dispatch_cgibs_publication_by_external_id",
        lambda requested_external_id: {
            "source": "CGIBS",
            "status": "NOT_FOUND",
            "published": 0,
            "external_id": requested_external_id
        },
        raising=False
    )

    response = client.post(
        f"/dispatch/cgibs/publications/{external_id}"
    )

    assert response.status_code == 404
    assert response.json()["detail"] == {
        "source": "CGIBS",
        "status": "NOT_FOUND",
        "published": 0,
        "external_id": external_id
    }


def test_endpoint_dispatch_nfe_deve_retornar_200(monkeypatch):
    monkeypatch.setattr(
        main,
        "dispatch_nfe_publications",
        lambda: {
            "source": "PORTAL_NFE",
            "collected": 2,
            "published": 2
        },
        raising=False
    )

    response = client.post("/dispatch/nfe")

    assert response.status_code == 200
    assert response.json() == {
        "source": "PORTAL_NFE",
        "collected": 2,
        "published": 2
    }


def test_endpoint_dispatch_nfe_unitario_deve_retornar_200(monkeypatch):
    external_id = "1" * 64

    monkeypatch.setattr(
        main,
        "dispatch_nfe_publication_by_external_id",
        lambda requested_external_id: {
            "source": "PORTAL_NFE",
            "status": "PUBLISHED",
            "published": 1,
            "publication": {
                "external_id": requested_external_id,
                "title": "Nota Técnica 2026.007 v1.10",
                "download_url": (
                    "https://www.nfe.fazenda.gov.br/portal/"
                    "exibirArquivo.aspx?conteudo=abc"
                )
            },
            "document": {
                "extraction_status": "EXTRACTED",
                "content_length": 28994,
                "content_hash": "hash-nfe",
                "extraction_error": None,
                "extractor_version": "portal-nfe-pypdf-v1",
                "extracted_at": "2026-10-05T23:18:10"
            }
        },
        raising=False
    )

    response = client.post(
        f"/dispatch/nfe/publications/{external_id}"
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "PORTAL_NFE"
    assert payload["status"] == "PUBLISHED"
    assert payload["published"] == 1
    assert payload["publication"]["external_id"] == external_id
    assert payload["document"]["extraction_status"] == "EXTRACTED"
    assert payload["document"]["extractor_version"] == (
        "portal-nfe-pypdf-v1"
    )
    assert "content_text" not in payload["document"]


def test_endpoint_dispatch_nfe_unitario_deve_retornar_404_quando_nao_encontrado(
    monkeypatch
):
    external_id = "2" * 64

    monkeypatch.setattr(
        main,
        "dispatch_nfe_publication_by_external_id",
        lambda requested_external_id: {
            "source": "PORTAL_NFE",
            "status": "NOT_FOUND",
            "published": 0,
            "external_id": requested_external_id
        },
        raising=False
    )

    response = client.post(
        f"/dispatch/nfe/publications/{external_id}"
    )

    assert response.status_code == 404
    assert response.json()["detail"] == {
        "source": "PORTAL_NFE",
        "status": "NOT_FOUND",
        "published": 0,
        "external_id": external_id
    }
