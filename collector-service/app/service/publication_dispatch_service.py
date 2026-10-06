from datetime import datetime

from app.collectors.cgibs_collector import get_cgibs_technical_publications
from app.collectors.imprensa_nacional_collector import (
    get_imprensa_nacional_publications
)
from app.collectors.nfe_collector import get_nfe_publications
from app.collectors.receita_collector import get_news_publications
from app.collectors.svrs_collector import get_publications
from app.messaging.publication_event_publisher import publish_publication_event
from app.model.publication_document import PublicationDocument
from app.service.document_extraction_service import extract_publication_document
from app.model.publication_event import (
    PUBLICATION_DISCOVERED,
    PublicationEvent
)
from app.service.publication_service import filter_relevant_publications


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


def dispatch_cgibs_publications() -> dict:
    publications = get_cgibs_technical_publications()

    published = 0

    for publication in publications:
        dispatch_publication(publication)
        published += 1

    return {
        "source": "CGIBS",
        "collected": len(publications),
        "published": published
    }


def dispatch_cgibs_publication_by_external_id(external_id: str) -> dict:
    publications = get_cgibs_technical_publications()

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
            "source": "CGIBS",
            "status": "NOT_FOUND",
            "published": 0,
            "external_id": external_id
        }

    return dispatch_publication(publication)


def dispatch_nfe_publications() -> dict:
    publications = get_nfe_publications()

    published = 0

    for publication in publications:
        dispatch_publication(publication)
        published += 1

    return {
        "source": "PORTAL_NFE",
        "collected": len(publications),
        "published": published
    }


def dispatch_nfe_publication_by_external_id(external_id: str) -> dict:
    publications = get_nfe_publications()

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
            "source": "PORTAL_NFE",
            "status": "NOT_FOUND",
            "published": 0,
            "external_id": external_id
        }

    return dispatch_publication(publication)


def dispatch_dou_publications() -> dict:
    publications = get_imprensa_nacional_publications()
    relevant_publications = filter_relevant_publications(publications)

    published = 0

    for publication in relevant_publications:
        dispatch_publication(publication)
        published += 1

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "collected": len(publications),
        "published": published
    }


def dispatch_dou_publication_by_external_id(external_id: str) -> dict:
    publications = get_imprensa_nacional_publications()

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
            "source": "IMPRENSA_NACIONAL_DOU",
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
