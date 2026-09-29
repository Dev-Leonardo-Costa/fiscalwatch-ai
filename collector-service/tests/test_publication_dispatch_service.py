from datetime import datetime

from app.model.publication import Publication
from app.model.publication_document import PublicationDocument
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
    monkeypatch.setattr(
        publication_dispatch_service,
        "extract_publication_document",
        lambda publication: PublicationDocument(
            source_url=publication.download_url,
            content_text=f"Documento {publication.external_id}",
            content_length=len(f"Documento {publication.external_id}"),
            extraction_status="EXTRACTED",
            extractor_version="svrs-pypdf-v1",
            extracted_at=datetime.now()
        )
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
    assert eventos_publicados[0].document.extraction_status == "EXTRACTED"
    assert eventos_publicados[1].event_type == PUBLICATION_DISCOVERED
    assert eventos_publicados[1].publication == segunda_publicacao
    assert eventos_publicados[1].document.extraction_status == "EXTRACTED"


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


def test_falha_em_um_documento_nao_impede_publicacao_dos_demais(
    monkeypatch
):
    primeira_publicacao = Publication(
        external_id="c" * 64,
        source="SVRS",
        title="Nota Técnica 2026.010 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        download_url="https://example.com/falha.pdf"
    )

    segunda_publicacao = Publication(
        external_id="d" * 64,
        source="SVRS",
        title="Nota Técnica 2026.011 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        download_url="https://example.com/sucesso.pdf"
    )

    eventos_publicados = []

    monkeypatch.setattr(
        publication_dispatch_service,
        "get_publications",
        lambda: [primeira_publicacao, segunda_publicacao]
    )

    def extrair_documento(publication):
        if publication == primeira_publicacao:
            raise RuntimeError("falha controlada")

        return PublicationDocument(
            source_url=publication.download_url,
            content_text="Documento extraido",
            content_length=len("Documento extraido"),
            extraction_status="EXTRACTED",
            extractor_version="svrs-pypdf-v1",
            extracted_at=datetime.now()
        )

    monkeypatch.setattr(
        publication_dispatch_service,
        "extract_publication_document",
        extrair_documento
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
    assert eventos_publicados[0].document.extraction_status == "FAILED"
    assert eventos_publicados[0].document.extraction_error == (
        "falha controlada"
    )
    assert eventos_publicados[1].document.extraction_status == "EXTRACTED"


def test_dispatch_unitario_encontra_publicacao_correta(monkeypatch):
    primeira_publicacao = criar_publicacao(
        external_id="e" * 64,
        title="Nota Técnica 2026.009 v1.00",
        download_url="https://example.com/nota.pdf"
    )
    segunda_publicacao = criar_publicacao(
        external_id="f" * 64,
        title="Schema XML",
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
        "extract_publication_document",
        lambda publication: PublicationDocument(
            source_url=publication.download_url,
            content_text="Texto extraido",
            content_length=len("Texto extraido"),
            content_hash="abc123",
            extraction_status="EXTRACTED",
            extractor_version="svrs-pypdf-v1",
            extracted_at=datetime.now()
        )
    )
    monkeypatch.setattr(
        publication_dispatch_service,
        "publish_publication_event",
        eventos_publicados.append
    )

    resultado = (
        publication_dispatch_service
        .dispatch_svrs_publication_by_external_id(
            primeira_publicacao.external_id
        )
    )

    assert resultado["source"] == "SVRS"
    assert resultado["status"] == "PUBLISHED"
    assert resultado["published"] == 1
    assert resultado["publication"] == {
        "external_id": primeira_publicacao.external_id,
        "title": primeira_publicacao.title,
        "download_url": primeira_publicacao.download_url
    }
    assert resultado["document"]["extraction_status"] == "EXTRACTED"
    assert resultado["document"]["content_length"] == len("Texto extraido")
    assert resultado["document"]["content_hash"] == "abc123"
    assert "content_text" not in resultado["document"]

    assert len(eventos_publicados) == 1
    assert eventos_publicados[0].publication == primeira_publicacao
    assert eventos_publicados[0].document is not None
    assert eventos_publicados[0].document.content_text == "Texto extraido"


def test_dispatch_unitario_external_id_inexistente_nao_publica(monkeypatch):
    publicacao = criar_publicacao(
        external_id="g" * 64,
        title="Nota Técnica 2026.009 v1.00",
        download_url="https://example.com/nota.pdf"
    )
    eventos_publicados = []

    monkeypatch.setattr(
        publication_dispatch_service,
        "get_publications",
        lambda: [publicacao]
    )
    monkeypatch.setattr(
        publication_dispatch_service,
        "publish_publication_event",
        eventos_publicados.append
    )

    resultado = (
        publication_dispatch_service
        .dispatch_svrs_publication_by_external_id("h" * 64)
    )

    assert resultado == {
        "source": "SVRS",
        "status": "NOT_FOUND",
        "published": 0,
        "external_id": "h" * 64
    }
    assert eventos_publicados == []


def criar_publicacao(
    external_id: str,
    title: str,
    download_url: str
) -> Publication:
    return Publication(
        external_id=external_id,
        source="SVRS",
        title=title,
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        download_url=download_url
    )
