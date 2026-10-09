from unittest.mock import Mock

import httpx
import pytest

from app.service import svrs_collection_state_client as client


@pytest.fixture
def response(monkeypatch):
    monkeypatch.setenv("FISCAL_SERVICE_BASE_URL", "http://127.0.0.1:8081")
    response = Mock()
    response.json.return_value = {"externalId": "a" * 64, "exists": False,
                                  "extractionStatus": None, "validDocument": False}
    request = Mock(return_value=response)
    monkeypatch.setattr(client.httpx, "get", request)
    return response, request


def test_consulta_estado_compacto_sem_filtrar_schema(response):
    _, request = response
    assert not client.fetch_collection_state("a" * 64, timeout=12).exists
    request.assert_called_once_with("http://127.0.0.1:8081/api/publications/collection-state",
                                    params={"externalId": "a" * 64}, timeout=12)


def test_erro_http_nao_vira_publicacao_ausente(response):
    reply, _ = response
    reply.raise_for_status.side_effect = httpx.HTTPError("indisponível")
    with pytest.raises(httpx.HTTPError):
        client.fetch_collection_state("a" * 64, timeout=12)


@pytest.mark.parametrize("change", [{"externalId": "b" * 64}, {"exists": "false"},
    {"validDocument": True}, {"extractionStatus": "FAILED"}])
def test_rejeita_resposta_ambigua(response, change):
    reply, _ = response
    reply.json.return_value.update(change)
    with pytest.raises(ValueError):
        client.fetch_collection_state("a" * 64, timeout=12)
