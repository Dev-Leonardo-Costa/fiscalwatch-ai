from datetime import datetime
import logging

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
from app.service.document_extraction_service import get_extractor_version_for_source
from app.service.fiscal_service_schema_impact_client import (
    send_schema_comparison_impact_analysis
)
from app.model.publication_event import (
    PUBLICATION_DISCOVERED,
    PublicationEvent
)
from app.service.publication_service import filter_relevant_publications
from app.service.svrs_schema_comparison_orchestration_service import (
    COMPARED,
    compare_current_svrs_schema_publication
)


logger = logging.getLogger(__name__)


def dispatch_publication(publication, *, timeout: float | None = None,
                         analyze_schema: bool = True, recover_only: bool = False) -> dict:
    try:
        document = extract_publication_document(publication, **(
            {} if timeout is None else {"timeout": timeout}))
    except Exception as exception:
        document = PublicationDocument(
            source_url=publication.download_url,
            extraction_status="FAILED",
            extraction_error=str(exception).splitlines()[0][:200],
            extractor_version=get_extractor_version_for_source(
                publication.source
            ),
            extracted_at=datetime.now()
        )

    if recover_only and (document.extraction_status != "EXTRACTED"
                         or not document.content_text or not document.content_text.strip()
                         or document.extraction_error is not None):
        result = _dispatch_result(publication, document)
        result.update(status="RECOVERY_NOT_READY", published=0)
        return result

    event = PublicationEvent(
        event_type=PUBLICATION_DISCOVERED,
        occurred_at=datetime.now(),
        publication=publication,
        document=document
    )

    publish_publication_event(event, **({} if timeout is None else {"timeout": timeout}))
    if analyze_schema:
        _try_dispatch_svrs_schema_impact_analysis(publication, document)

    return _dispatch_result(publication, document)


def _try_dispatch_svrs_schema_impact_analysis(
    publication,
    document: PublicationDocument
) -> None:
    if not _should_analyze_svrs_schema(publication, document):
        return

    try:
        comparison = compare_current_svrs_schema_publication(
            publication,
            document
        )

        if comparison.status != COMPARED:
            logger.info(
                "Comparação de schema SVRS ignorada. external_id=%s "
                "status=%s reason=%s",
                publication.external_id,
                comparison.status,
                comparison.reason
            )
            return

        send_schema_comparison_impact_analysis(comparison)
    except Exception:
        logger.exception(
            "Falha no fluxo adicional de impacto de schema SVRS. "
            "external_id=%s",
            publication.external_id
        )


def _should_analyze_svrs_schema(publication, document: PublicationDocument):
    return (
        publication.source == "SVRS"
        and publication.document_type == "SCHEMA"
        and document.extraction_status == "EXTRACTED"
    )


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
