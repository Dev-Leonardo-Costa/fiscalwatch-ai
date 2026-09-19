from fastapi import FastAPI
from app.collectors.svrs_collector import filter_recent_publications

from app.collectors.svrs_collector import (
    fetch_svrs_page,
    parse_svrs_page,
    inspect_document,
    find_download_script,
    build_download_url,
    inspect_publication_container,
    get_publications,
    filter_recent_publications,
    download_document,
    extract_pdf_text,
    normalize_text
)


app = FastAPI(
    title="FiscalWatch Collector Service",
    version="0.1.0"
)


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "collector-service"
    }


@app.get("/test/svrs")
def test_svrs():
    html = fetch_svrs_page()
    links = parse_svrs_page(html)

    return {
        "source": "SVRS",
        "status": "SUCCESS",
        "total_links": len(links),
        "links": links[:30]
    }


@app.get("/test/svrs/document")
def test_svrs_document():
    html = fetch_svrs_page()

    document = inspect_document(
        html,
        "Evento 211110 - NT 2025.002 v1.40"
    )

    return {
        "source": "SVRS",
        "document": document
    }


@app.get("/test/svrs/download-script")
def test_download_script():
    html = fetch_svrs_page()

    script = find_download_script(html)

    return {
        "source": "SVRS",
        "script": script
    }


@app.get("/test/svrs/download-url")
def test_download_url():
    onclick = (
        "download_arquivo_estatico("
        "'NFE', 2, "
        "'Schema_Evento_211110_NT2025.002 v1.40.zip');"
    )

    url = build_download_url(onclick)

    return {
        "download_url": url
    }


@app.get("/test/svrs/publication")
def test_svrs_publication():
    html = fetch_svrs_page()

    publication = inspect_publication_container(
        html,
        "Evento 211110 - NT 2025.002 v1.40"
    )

    return {
        "source": "SVRS",
        "html": publication
    }


@app.get("/publications")
def publications():
    return get_publications()


@app.get("/publications/recent")
def recent_publications(hours: int = 72):

    publications = get_publications()

    recent = filter_recent_publications(
        publications,
        hours
    )

    return {
        "source": "SVRS",
        "period_hours": hours,
        "total": len(recent),
        "publications": recent
    }


@app.get("/test/svrs/download")
def test_download():

    publications = get_publications()

    publication = next(
        (
            publication
            for publication in publications
            if publication.title == "Nota Técnica 2026.009 v.1.00"
        ),
        None
    )

    if publication is None:
        return {
            "status": "NOT_FOUND"
        }

    if not publication.download_url:
        return {
            "status": "NO_DOWNLOAD_URL"
        }

    file_path = download_document(
        publication.download_url,
        "NT2026.009_v1.00.pdf"
    )

    return {
        "status": "SUCCESS",
        "title": publication.title,
        "file": file_path
    }


@app.get("/test/svrs/pdf-text")
def test_pdf_text():

    file_path = "downloads/NT2026.009_v1.00.pdf"

    raw_text = extract_pdf_text(file_path)

    normalized_text = normalize_text(raw_text)

    return {
        "raw_characters": len(raw_text),
        "normalized_characters": len(normalized_text),
        "preview": normalized_text[:2000]
    }



    