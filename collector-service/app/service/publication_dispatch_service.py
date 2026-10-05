from datetime import datetime

from app.collectors.receita_collector import get_news_publications
from app.collectors.svrs_collector import get_publications
from app.messaging.publication_event_publisher import publish_publication_event
from app.model.publication_document import PublicationDocument
from app.service.document_extraction_service import extract_publication_document
from app.model.publication_event import (
    PUBLICATION_DISCOVERED,
    PublicationEvent
)


def dispatch_publication(publication) -> dict:
    try:
        document = extract_publication_document(publication)
    except Exception as exception:
        document = PublicationDocument(
            source_url=publication.download_url,
            extraction_status="FAILED",
            extraction_error=str(exception).splitlines()[0][:200],
            extractor_version="svrs-pypdf-v1",
            extracted_at=datetime.now()
        )

    event = PublicationEvent(
        event_type=PUBLICATION_DISCOVERED,
        occurred_at=datetime.now(),
        publication=publication,
        document=document
    )

    publish_publication_event(event)

    return _dispatch_result(publication, document)


def dispatch_svrs_publications() -> dict:
    publications = get_publications()

    published = 0

    for publication in publications:
        dispatch_publication(publication)
        published += 1

    return {
        "source": "SVRS",
        "collected": len(publications),
        "published": published
    }


def dispatch_svrs_publication_by_external_id(external_id: str) -> dict:
    publications = get_publications()

    publication = next(
        (
            publication
            for publication in publications
            if publication.external_id == external_id
        ),
        None
    )

    if publication is None:
        return {
            "source": "SVRS",
            "status": "NOT_FOUND",
            "published": 0,
            "external_id": external_id
        }

    return dispatch_publication(publication)


def dispatch_receita_publications() -> dict:
    publications = get_news_publications()

    published = 0

    for publication in publications:
        dispatch_publication(publication)
        published += 1

    return {
        "source": "RECEITA_FEDERAL",
        "collected": len(publications),
        "published": published
    }


def dispatch_receita_publication_by_external_id(external_id: str) -> dict:
    publications = get_news_publications()

    publication = next(
        (
            publication
            for publication in publications
            if publication.external_id == external_id
        ),
        None
    )

    if publication is None:
        return {
            "source": "RECEITA_FEDERAL",
            "status": "NOT_FOUND",
            "published": 0,
            "external_id": external_id
        }

    return dispatch_publication(publication)


def _dispatch_result(publication, document: PublicationDocument) -> dict:
    return {
        "source": publication.source,
        "status": "PUBLISHED",
        "published": 1,
        "publication": {
            "external_id": publication.external_id,
            "title": publication.title,
            "download_url": publication.download_url
        },
        "document": {
            "extraction_status": document.extraction_status,
            "content_length": document.content_length,
            "content_hash": document.content_hash,
            "extraction_error": document.extraction_error,
            "extractor_version": document.extractor_version,
            "extracted_at": document.extracted_at
        }
    }
