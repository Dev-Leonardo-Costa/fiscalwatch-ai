import json
import hashlib
from datetime import datetime

from app.model.publication import Publication
from app.model.publication_document import PublicationDocument
from app.model.publication_event import PublicationEvent


def test_publication_event_sem_document_continua_valido():
    event = PublicationEvent(
        event_type="publication.discovered",
        occurred_at=datetime.now(),
        publication=criar_publicacao()
    )

    assert event.document is None


def test_deve_serializar_evento_com_document():
    content_text = "Texto normalizado"

    event = PublicationEvent(
        event_type="publication.discovered",
        occurred_at=datetime.now(),
        publication=criar_publicacao(),
        document=PublicationDocument(
            source_url="https://example.com/nota.pdf",
            content_text=content_text,
            content_hash=hashlib.sha256(
                content_text.encode("utf-8")
            ).hexdigest(),
            content_length=len(content_text),
            extraction_status="EXTRACTED",
            extractor_version="svrs-pypdf-v1",
            extracted_at=datetime.now()
        )
    )

    payload = json.loads(event.model_dump_json())

    assert payload["document"]["source_url"] == (
        "https://example.com/nota.pdf"
    )
    assert payload["document"]["content_text"] == content_text
    assert payload["document"]["content_length"] == len(content_text)
    assert payload["document"]["extraction_status"] == "EXTRACTED"


def test_json_publicado_continua_em_snake_case():
    event = PublicationEvent(
        event_type="publication.discovered",
        occurred_at=datetime.now(),
        publication=criar_publicacao(),
        document=PublicationDocument(
            source_url="https://example.com/nota.pdf",
            extraction_status="PENDING"
        )
    )

    payload = json.loads(event.model_dump_json())

    assert "event_type" in payload
    assert "occurred_at" in payload
    assert "published_at" in payload["publication"]
    assert "download_url" in payload["publication"]
    assert "source_url" in payload["document"]
    assert "extraction_status" in payload["document"]
    assert "contentText" not in payload["document"]


def criar_publicacao() -> Publication:
    return Publication(
        external_id="a" * 64,
        source="SVRS",
        title="Nota Técnica 2026.009 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        download_url="https://example.com/nota.pdf"
    )
