from datetime import datetime

from app.model.publication import Publication
from app.model.publication_event import PUBLICATION_DISCOVERED
from app.service import publication_dispatch_service


def test_deve_publicar_evento_para_cada_publicacao_svrs(monkeypatch):
    primeira_publicacao = Publication(
        external_id="a" * 64,
        source="SVRS",
        title="Nota Técnica 2026.009 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        description="Primeira publicação",
        download_url="https://example.com/primeira.pdf"
    )

    segunda_publicacao = Publication(
        external_id="b" * 64,
        source="SVRS",
        title="Schema XML",
        document_type="SCHEMA",
        published_at=datetime.now(),
        description="Segunda publicação",
        download_url="https://example.com/schema.zip"
    )

    eventos_publicados = []

    monkeypatch.setattr(
        publication_dispatch_service,
        "get_publications",
        lambda: [primeira_publicacao, segunda_publicacao]
    )

    monkeypatch.setattr(
        publication_dispatch_service,
        "publish_publication_event",
        eventos_publicados.append
    )

    resultado = publication_dispatch_service.dispatch_svrs_publications()

    assert resultado == {
        "source": "SVRS",
        "collected": 2,
        "published": 2
    }

    assert len(eventos_publicados) == 2
    assert eventos_publicados[0].event_type == PUBLICATION_DISCOVERED
    assert eventos_publicados[0].publication == primeira_publicacao
    assert eventos_publicados[1].event_type == PUBLICATION_DISCOVERED
    assert eventos_publicados[1].publication == segunda_publicacao


def test_deve_retornar_zero_quando_nao_houver_publicacoes(monkeypatch):
    eventos_publicados = []

    monkeypatch.setattr(
        publication_dispatch_service,
        "get_publications",
        lambda: []
    )

    monkeypatch.setattr(
        publication_dispatch_service,
        "publish_publication_event",
        eventos_publicados.append
    )

    resultado = publication_dispatch_service.dispatch_svrs_publications()

    assert resultado == {
        "source": "SVRS",
        "collected": 0,
        "published": 0
    }

    assert eventos_publicados == []
