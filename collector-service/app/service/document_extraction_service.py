import hashlib
import httpx
import posixpath
import zipfile
from datetime import datetime
from pathlib import Path
from uuid import uuid4
from urllib.parse import parse_qs, unquote, urlparse

from bs4 import BeautifulSoup

from app.collectors import svrs_collector
from app.collectors import receita_collector
from app.collectors import cgibs_collector
from app.collectors import imprensa_nacional_collector
from app.model.publication import Publication
from app.model.publication_document import PublicationDocument


EXTRACTOR_VERSION = "svrs-pypdf-v1"
SVRS_SCHEMA_ZIP_EXTRACTOR_VERSION = "svrs-schema-zip-v1"
CGIBS_EXTRACTOR_VERSION = "cgibs-pypdf-v1"
PORTAL_NFE_EXTRACTOR_VERSION = "portal-nfe-pypdf-v1"
DOU_EXTRACTOR_VERSION = "dou-html-v1"

SVRS_SCHEMA_ZIP_MAX_FILES = 100
SVRS_SCHEMA_ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES = 1_000_000
SVRS_SCHEMA_ZIP_MAX_FILE_UNCOMPRESSED_BYTES = 2_000_000
SVRS_SCHEMA_ZIP_MAX_COMPRESSION_RATIO = 100


def get_extractor_version_for_source(source: str) -> str | None:
    extractor_versions = {
        "SVRS": EXTRACTOR_VERSION,
        "RECEITA_FEDERAL": receita_collector.HTML_EXTRACTOR_VERSION,
        "CGIBS": CGIBS_EXTRACTOR_VERSION,
        "PORTAL_NFE": PORTAL_NFE_EXTRACTOR_VERSION,
        "IMPRENSA_NACIONAL_DOU": DOU_EXTRACTOR_VERSION
    }

    return extractor_versions.get(source)


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

    return _extract_svrs_document(publication)


def _extract_svrs_document(publication: Publication) -> PublicationDocument:
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

        magic_bytes = _read_magic_bytes(file_path)
        apparent_extension = _apparent_file_extension(
            publication.download_url,
            file_path
        )

        if magic_bytes.startswith(b"%PDF"):
            return _extract_pdf_file(
                publication.download_url,
                file_path,
                EXTRACTOR_VERSION
            )

        if magic_bytes.startswith(b"PK"):
            if apparent_extension == "zip":
                return _extract_svrs_schema_zip_document(
                    publication.download_url,
                    file_path
                )

            return PublicationDocument(
                source_url=publication.download_url,
                extraction_status="PENDING",
                extraction_error=(
                    f"formato ainda nao suportado: {apparent_extension}"
                ),
                extracted_at=datetime.now()
            )

        return PublicationDocument(
            source_url=publication.download_url,
            extraction_status="PENDING",
            extraction_error=(
                f"formato ainda nao suportado: {apparent_extension}"
            ),
            extracted_at=datetime.now()
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

        return _extract_pdf_file(
            publication.download_url,
            file_path,
            extractor_version
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


def _extract_pdf_file(
    source_url: str,
    file_path: str,
    extractor_version: str
) -> PublicationDocument:
    extracted_text = svrs_collector.extract_pdf_text(file_path)
    normalized_text = svrs_collector.normalize_text(extracted_text)
    extracted_at = datetime.now()

    if not normalized_text:
        return PublicationDocument(
            source_url=source_url,
            content_text="",
            content_length=0,
            extraction_status="EMPTY",
            extraction_error=None,
            extractor_version=extractor_version,
            extracted_at=extracted_at
        )

    return PublicationDocument(
        source_url=source_url,
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


def _temporary_filename(publication: Publication) -> str:
    return f"{publication.external_id}-{uuid4().hex}.pdf"


def _read_magic_bytes(file_path: str) -> bytes:
    with open(file_path, "rb") as file:
        return file.read(8)


def _apparent_file_extension(source_url: str, file_path: str) -> str:
    query = parse_qs(urlparse(source_url).query)
    file_name = query.get("nomeArquivo", [""])[0]

    if not file_name:
        file_name = Path(urlparse(source_url).path).name

    if not file_name:
        file_name = Path(file_path).name

    suffix = Path(unquote(file_name)).suffix

    return suffix.lstrip(".").lower() or "desconhecido"


def _extract_svrs_schema_zip_document(
    source_url: str,
    file_path: str
) -> PublicationDocument:
    try:
        content_text = _read_svrs_schema_zip_content(file_path)
        extracted_at = datetime.now()

        if not content_text:
            return PublicationDocument(
                source_url=source_url,
                content_text="",
                content_length=0,
                extraction_status="EMPTY",
                extraction_error=None,
                extractor_version=SVRS_SCHEMA_ZIP_EXTRACTOR_VERSION,
                extracted_at=extracted_at
            )

        return PublicationDocument(
            source_url=source_url,
            content_text=content_text,
            content_hash=hashlib.sha256(
                content_text.encode("utf-8")
            ).hexdigest(),
            content_length=len(content_text),
            extraction_status="EXTRACTED",
            extraction_error=None,
            extractor_version=SVRS_SCHEMA_ZIP_EXTRACTOR_VERSION,
            extracted_at=extracted_at
        )

    except Exception as exception:
        return PublicationDocument(
            source_url=source_url,
            extraction_status="FAILED",
            extraction_error=_safe_error_message(exception),
            extractor_version=SVRS_SCHEMA_ZIP_EXTRACTOR_VERSION,
            extracted_at=datetime.now()
        )


def _read_svrs_schema_zip_content(file_path: str) -> str:
    with zipfile.ZipFile(file_path) as archive:
        file_infos = [
            info
            for info in archive.infolist()
            if not info.is_dir()
        ]

        _validate_svrs_schema_zip_infos(file_infos)

        schema_infos = [
            info
            for info in file_infos
            if _is_allowed_schema_path(info.filename)
        ]

        if not schema_infos:
            return ""

        parts = []

        for info in sorted(
            schema_infos,
            key=lambda item: _normalize_zip_path(item.filename)
        ):
            normalized_path = _normalize_zip_path(info.filename)
            content = archive.read(info).decode("utf-8-sig")
            parts.append(
                f"=== arquivo: {normalized_path} ===\n"
                f"{content.strip()}\n"
            )

        return "\n".join(parts).strip()


def _validate_svrs_schema_zip_infos(
    file_infos: list[zipfile.ZipInfo]
) -> None:
    if len(file_infos) > SVRS_SCHEMA_ZIP_MAX_FILES:
        raise ValueError("quantidade de arquivos excede o limite")

    total_size = 0

    for info in file_infos:
        normalized_path = _normalize_zip_path(info.filename)

        if _is_unsafe_zip_path(info.filename, normalized_path):
            raise ValueError("caminho inseguro no ZIP")

        if info.file_size > SVRS_SCHEMA_ZIP_MAX_FILE_UNCOMPRESSED_BYTES:
            raise ValueError("arquivo interno excede o limite")

        total_size += info.file_size

        if total_size > SVRS_SCHEMA_ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES:
            raise ValueError(
                "tamanho total descompactado excede o limite"
            )

        if info.compress_size > 0:
            compression_ratio = info.file_size / info.compress_size

            if compression_ratio > SVRS_SCHEMA_ZIP_MAX_COMPRESSION_RATIO:
                raise ValueError("razao de compressao excede o limite")


def _normalize_zip_path(path: str) -> str:
    return posixpath.normpath(path.replace("\\", "/"))


def _is_unsafe_zip_path(path: str, normalized_path: str) -> bool:
    first_segment = path.split("/", 1)[0].split("\\", 1)[0]

    return (
        path.startswith("/")
        or path.startswith("\\")
        or ":" in first_segment
        or normalized_path.startswith("../")
        or normalized_path == ".."
        or "/../" in f"/{normalized_path}/"
    )


def _is_allowed_schema_path(path: str) -> bool:
    extension = Path(_normalize_zip_path(path)).suffix.lower()

    return extension in {".xsd", ".xml"}


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
