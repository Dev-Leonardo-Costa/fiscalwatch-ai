import hashlib
import httpx
from datetime import datetime
from pathlib import Path
from uuid import uuid4

from bs4 import BeautifulSoup

from app.collectors import svrs_collector
from app.collectors import receita_collector
from app.collectors import cgibs_collector
from app.collectors import imprensa_nacional_collector
from app.model.publication import Publication
from app.model.publication_document import PublicationDocument


EXTRACTOR_VERSION = "svrs-pypdf-v1"
CGIBS_EXTRACTOR_VERSION = "cgibs-pypdf-v1"
PORTAL_NFE_EXTRACTOR_VERSION = "portal-nfe-pypdf-v1"
DOU_EXTRACTOR_VERSION = "dou-html-v1"


def extract_publication_document(
    publication: Publication
) -> PublicationDocument:
    if publication.source == "RECEITA_FEDERAL":
        return _extract_receita_html_document(publication)

    if publication.source == "CGIBS":
        return _extract_cgibs_pdf_document(publication)

    if publication.source == "PORTAL_NFE":
        return _extract_direct_pdf_document(
            publication,
            PORTAL_NFE_EXTRACTOR_VERSION
        )

    if publication.source == "IMPRENSA_NACIONAL_DOU":
        return _extract_dou_html_document(publication)

    return _extract_direct_pdf_document(publication, EXTRACTOR_VERSION)


def _extract_direct_pdf_document(
    publication: Publication,
    extractor_version: str
) -> PublicationDocument:
    if not publication.download_url:
        return PublicationDocument(
            source_url=None,
            extraction_status="PENDING"
        )

    file_path = None

    try:
        file_path = svrs_collector.download_document(
            publication.download_url,
            _temporary_filename(publication)
        )

        extracted_text = svrs_collector.extract_pdf_text(file_path)
        normalized_text = svrs_collector.normalize_text(extracted_text)
        extracted_at = datetime.now()

        if not normalized_text:
            return PublicationDocument(
                source_url=publication.download_url,
                content_text="",
                content_length=0,
                extraction_status="EMPTY",
                extraction_error=None,
                extractor_version=extractor_version,
                extracted_at=extracted_at
            )

        return PublicationDocument(
            source_url=publication.download_url,
            content_text=normalized_text,
            content_hash=hashlib.sha256(
                normalized_text.encode("utf-8")
            ).hexdigest(),
            content_length=len(normalized_text),
            extraction_status="EXTRACTED",
            extraction_error=None,
            extractor_version=extractor_version,
            extracted_at=extracted_at
        )

    except Exception as exception:
        return PublicationDocument(
            source_url=publication.download_url,
            extraction_status="FAILED",
            extraction_error=_safe_error_message(exception),
            extractor_version=extractor_version,
            extracted_at=datetime.now()
        )

    finally:
        if file_path:
            _delete_temporary_file(file_path)


def _temporary_filename(publication: Publication) -> str:
    return f"{publication.external_id}-{uuid4().hex}.pdf"


def _delete_temporary_file(file_path: str) -> None:
    try:
        Path(file_path).unlink(missing_ok=True)
    except OSError:
        pass


def _safe_error_message(exception: Exception) -> str:
    message = str(exception).splitlines()[0].strip()

    if not message:
        message = exception.__class__.__name__

    return message[:200]


def _extract_receita_html_document(
    publication: Publication
) -> PublicationDocument:
    if not publication.download_url:
        return PublicationDocument(
            source_url=None,
            extraction_status="PENDING"
        )

    try:
        return receita_collector.extract_news_document(
            publication.download_url
        )
    except Exception as exception:
        return PublicationDocument(
            source_url=publication.download_url,
            extraction_status="FAILED",
            extraction_error=_safe_error_message(exception),
            extractor_version=receita_collector.HTML_EXTRACTOR_VERSION,
            extracted_at=datetime.now()
        )


def _extract_dou_html_document(
    publication: Publication
) -> PublicationDocument:
    if not publication.download_url:
        return PublicationDocument(
            source_url=None,
            extraction_status="PENDING"
        )

    try:
        response = httpx.get(
            publication.download_url,
            headers=imprensa_nacional_collector.DOU_HTTP_HEADERS,
            timeout=30.0,
            follow_redirects=True
        )

        response.raise_for_status()

        content_text = _extract_dou_main_content_text(response.text)
        extracted_at = datetime.now()

        return PublicationDocument(
            source_url=publication.download_url,
            content_text=content_text,
            content_hash=hashlib.sha256(
                content_text.encode("utf-8")
            ).hexdigest(),
            content_length=len(content_text),
            extraction_status="EXTRACTED",
            extraction_error=None,
            extractor_version=DOU_EXTRACTOR_VERSION,
            extracted_at=extracted_at
        )

    except Exception as exception:
        return PublicationDocument(
            source_url=publication.download_url,
            extraction_status="FAILED",
            extraction_error=_safe_error_message(exception),
            extractor_version=DOU_EXTRACTOR_VERSION,
            extracted_at=datetime.now()
        )


def _extract_dou_main_content_text(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")

    content = soup.select_one("article#materia div.texto-dou")

    if not content:
        content = soup.select_one("div.texto-dou")

    if not content:
        raise ValueError("conteudo principal do DOU nao encontrado")

    text = svrs_collector.normalize_text(
        content.get_text(
            "\n",
            strip=True
        )
    )

    if not text:
        raise ValueError("conteudo principal do DOU nao encontrado")

    return text


def _extract_cgibs_pdf_document(
    publication: Publication
) -> PublicationDocument:
    if not publication.download_url:
        return PublicationDocument(
            source_url=None,
            extraction_status="PENDING"
        )

    selected_file = cgibs_collector.select_cgibs_main_technical_file(
        cgibs_collector.parse_cgibs_technical_files(
            publication.download_url
        )
    )

    if not selected_file:
        return PublicationDocument(
            source_url=None,
            extraction_status="PENDING"
        )

    pdf_url = selected_file["url"]
    file_path = None

    try:
        file_path = svrs_collector.download_document(
            pdf_url,
            _temporary_filename(publication)
        )

        extracted_text = svrs_collector.extract_pdf_text(file_path)
        normalized_text = svrs_collector.normalize_text(extracted_text)
        extracted_at = datetime.now()

        if not normalized_text:
            return PublicationDocument(
                source_url=pdf_url,
                content_text="",
                content_length=0,
                extraction_status="EMPTY",
                extraction_error=None,
                extractor_version=CGIBS_EXTRACTOR_VERSION,
                extracted_at=extracted_at
            )

        return PublicationDocument(
            source_url=pdf_url,
            content_text=normalized_text,
            content_hash=hashlib.sha256(
                normalized_text.encode("utf-8")
            ).hexdigest(),
            content_length=len(normalized_text),
            extraction_status="EXTRACTED",
            extraction_error=None,
            extractor_version=CGIBS_EXTRACTOR_VERSION,
            extracted_at=extracted_at
        )

    except Exception as exception:
        return PublicationDocument(
            source_url=pdf_url,
            extraction_status="FAILED",
            extraction_error=_safe_error_message(exception),
            extractor_version=CGIBS_EXTRACTOR_VERSION,
            extracted_at=datetime.now()
        )

    finally:
        if file_path:
            _delete_temporary_file(file_path)
