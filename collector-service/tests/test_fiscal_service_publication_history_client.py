from datetime import datetime

import httpx
import pytest

from app.service.fiscal_service_publication_history_client import (
    FISCAL_SERVICE_BASE_URL_ENV,
    fetch_svrs_schema_publication_history
)


def test_deve_consultar_historico_svrs_schema_com_url_base_do_ambiente(
    monkeypatch
):
    chamadas = []

    def fake_get(url, params, timeout):
        chamadas.append({
            "url": url,
            "params": params,
            "timeout": timeout
        })

        return RespostaHttp([
            criar_publicacao_historico()
        ])

    monkeypatch.setenv(
        FISCAL_SERVICE_BASE_URL_ENV,
        "http://fiscal-service:8080"
    )
    monkeypatch.setattr(
        "app.service.fiscal_service_publication_history_client.httpx.get",
        fake_get
    )

    result = fetch_svrs_schema_publication_history()

    assert chamadas == [
        {
            "url": (
                "http://fiscal-service:8080/api/publications/history"
            ),
            "params": {
                "source": "SVRS",
                "documentType": "SCHEMA"
            },
            "timeout": 10.0
        }
    ]
    assert len(result) == 1
    assert result[0].external_id == "a" * 64


def test_deve_aceitar_url_base_informada_explicitamente(monkeypatch):
    chamadas = []

    def fake_get(url, params, timeout):
        chamadas.append(url)

        return RespostaHttp([])

    monkeypatch.setattr(
        "app.service.fiscal_service_publication_history_client.httpx.get",
        fake_get
    )

    result = fetch_svrs_schema_publication_history(
        base_url="http://localhost:8080/"
    )

    assert result == []
    assert chamadas == [
        "http://localhost:8080/api/publications/history"
    ]


def test_deve_converter_json_para_modelos_pydantic(monkeypatch):
    monkeypatch.setattr(
        "app.service.fiscal_service_publication_history_client.httpx.get",
        lambda url, params, timeout: RespostaHttp([
            criar_publicacao_historico()
        ])
    )

    result = fetch_svrs_schema_publication_history(
        base_url="http://localhost:8080"
    )

    publication = result[0]

    assert publication.id == 10
    assert publication.external_id == "a" * 64
    assert publication.source == "SVRS"
    assert publication.title == "Pacote de schemas"
    assert publication.document_type == "SCHEMA"
    assert publication.published_at == datetime(2026, 1, 1, 0, 0)
    assert publication.download_url == "https://example.com/schema.zip"
    assert publication.document.content_text == (
        "=== arquivo: schema.xsd ===\n<xs:schema/>"
    )
    assert publication.document.content_hash == "b" * 64
    assert publication.document.content_length == 38
    assert publication.document.extraction_status == "EXTRACTED"
    assert publication.document.extractor_version == "svrs-schema-zip-v1"
    assert publication.document.extracted_at == datetime(2026, 1, 2, 10, 0)


def test_deve_exigir_url_base_quando_ambiente_nao_estiver_configurado(
    monkeypatch
):
    monkeypatch.delenv(FISCAL_SERVICE_BASE_URL_ENV, raising=False)

    with pytest.raises(ValueError, match=FISCAL_SERVICE_BASE_URL_ENV):
        fetch_svrs_schema_publication_history()


def test_deve_propagar_erro_http_sem_esconder_causa(monkeypatch):
    error = httpx.HTTPStatusError(
        "500 Server Error",
        request=httpx.Request("GET", "http://localhost"),
        response=httpx.Response(500)
    )

    monkeypatch.setattr(
        "app.service.fiscal_service_publication_history_client.httpx.get",
        lambda url, params, timeout: RespostaHttp([], error)
    )

    with pytest.raises(httpx.HTTPStatusError) as exc_info:
        fetch_svrs_schema_publication_history(
            base_url="http://localhost:8080"
        )

    assert exc_info.value is error


class RespostaHttp:

    def __init__(self, payload, error=None):
        self.payload = payload
        self.error = error

    def raise_for_status(self):
        if self.error:
            raise self.error

    def json(self):
        return self.payload


def criar_publicacao_historico():
    return {
        "id": 10,
        "externalId": "a" * 64,
        "source": "SVRS",
        "title": "Pacote de schemas",
        "documentType": "SCHEMA",
        "publishedAt": "2026-01-01T00:00:00",
        "downloadUrl": "https://example.com/schema.zip",
        "document": {
            "contentText": "=== arquivo: schema.xsd ===\n<xs:schema/>",
            "contentHash": "b" * 64,
            "contentLength": 38,
            "extractionStatus": "EXTRACTED",
            "extractorVersion": "svrs-schema-zip-v1",
            "extractedAt": "2026-01-02T10:00:00"
        }
    }
