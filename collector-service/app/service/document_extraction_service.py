import hashlib
from datetime import datetime
from pathlib import Path
from uuid import uuid4

from app.collectors import svrs_collector
from app.model.publication import Publication
from app.model.publication_document import PublicationDocument


EXTRACTOR_VERSION = "svrs-pypdf-v1"


def extract_publication_document(
    publication: Publication
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
                extractor_version=EXTRACTOR_VERSION,
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
            extractor_version=EXTRACTOR_VERSION,
            extracted_at=extracted_at
        )

    except Exception as exception:
        return PublicationDocument(
            source_url=publication.download_url,
            extraction_status="FAILED",
            extraction_error=_safe_error_message(exception),
            extractor_version=EXTRACTOR_VERSION,
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
