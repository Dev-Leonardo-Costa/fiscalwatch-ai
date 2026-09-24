import httpx


from bs4 import BeautifulSoup
from datetime import datetime
from app.model.publication import Publication
from app.service.publication_identity_service import generate_external_id


CGIBS_URL = "https://www.cgibs.gov.br"


def fetch_cgibs_page() -> str:
    response = httpx.get(
        CGIBS_URL,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def parse_cgibs_links(html: str) -> list[dict]:

    soup = BeautifulSoup(html, "html.parser")

    links = []

    for link in soup.find_all("a"):
        text = link.get_text(" ", strip=True)
        href = link.get("href")

        if text and href:
            links.append({
                "text": text,
                "href": href
            })

    return links


CGIBS_DOCUMENTOS_TECNICOS_URL = (
    "https://www.cgibs.gov.br/documentos-tecnicos"
)


def fetch_cgibs_technical_documents_page() -> str:
    response = httpx.get(
        CGIBS_DOCUMENTOS_TECNICOS_URL,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def inspect_technical_documents_page(html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")

    section = soup.find(
        "section",
        id=lambda value: value and value.startswith("pagedlistItens")
    )

    if not section:
        return {
            "section_found": False,
            "content": None
        }

    return {
        "section_found": True,
        "content": section.get_text(" ", strip=True),
        "html": str(section)
    }


CGIBS_TECHNICAL_DOCUMENTS_API = (
    "https://www.cgibs.gov.br/_service/conteudo/pagedlistfilho"
)


def fetch_cgibs_technical_documents_api() -> dict:

    params = [
        ("id", "156"),
        ("conteudopai", "156"),
        ("templatename", "pagina.listapagina.cards.fullheader"),
        ("currentPage", "1"),
        ("pageSize", "9"),
        ("fields[]", "Titulo"),
        ("fields[]", "TituloCurto"),
        ("fields[]", "Texto"),
        ("form[ordem]", "RECENTES"),
    ]

    response = httpx.get(
        CGIBS_TECHNICAL_DOCUMENTS_API,
        params=params,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.json()


def inspect_cgibs_scripts(html: str) -> list[dict]:
    soup = BeautifulSoup(html, "html.parser")

    scripts = []

    for script in soup.find_all("script"):
        src = script.get("src")
        content = script.get_text(" ", strip=True)

        if (
            (src and "paged" in src.lower())
            or "pagedlist" in content.lower()
            or "_service/conteudo" in content.lower()
        ):
            scripts.append({
                "src": src,
                "content": content[:2000] if content else None
            })

    return scripts


CGIBS_PAGED_LIST_JS = (
    "https://www.cgibs.gov.br/"
    "matriz_common/versions/2.1.5/js/procergs/"
    "jquery.matrizPagedList.js?2.1.5.161.3+1"
)


def fetch_cgibs_paged_list_js() -> str:
    response = httpx.get(
        CGIBS_PAGED_LIST_JS,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def get_cgibs_script_sources(html: str) -> list[str]:
    soup = BeautifulSoup(html, "html.parser")

    scripts = []

    for script in soup.find_all("script"):
        src = script.get("src")

        if src:
            scripts.append(src)

    return scripts


CGIBS_MATRIZ_UI_JS = (
    "https://www.cgibs.gov.br/"
    "matriz_common/versions/2.1.5/js/procergs/"
    "matriz.ui.js?2.1.5.161.3+1"
)


def fetch_cgibs_matriz_ui_js() -> str:
    response = httpx.get(
        CGIBS_MATRIZ_UI_JS,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def parse_cgibs_technical_documents(body: str) -> list[dict]:

    soup = BeautifulSoup(body, "html.parser")

    documents = []

    items = soup.select(".artigo__listapaginas__item")

    for item in items:

        title_element = item.select_one(
            ".artigo__listapaginas__item__titulo a"
        )

        description_element = item.select_one(
            ".artigo__listapaginas__item__descricao"
        )

        if not title_element:
            continue

        title = title_element.get_text(" ", strip=True)
        href = title_element.get("href")

        if href and href.startswith("/"):
            href = f"{CGIBS_URL}{href}"

        description = (
            description_element.get_text(" ", strip=True)
            if description_element
            else None
        )

        documents.append({
            "title": title,
            "url": href,
            "description": description or None
        })

    return documents


def fetch_cgibs_document_page(url: str) -> str:
    response = httpx.get(
        url,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def inspect_cgibs_document_page(url: str) -> dict:

    html = fetch_cgibs_document_page(url)

    soup = BeautifulSoup(html, "html.parser")

    title_element = soup.find("h1")

    links = []

    for link in soup.find_all("a"):
        text = link.get_text(" ", strip=True)
        href = link.get("href")

        if text and href:
            links.append({
                "text": text,
                "href": href
            })

    return {
        "title": (
            title_element.get_text(" ", strip=True)
            if title_element
            else None
        ),
        "html_size": len(html),
        "total_links": len(links),
        "links": links
    }


def parse_cgibs_technical_files(url: str) -> list[dict]:

    html = fetch_cgibs_document_page(url)

    soup = BeautifulSoup(html, "html.parser")

    files = []

    for link in soup.find_all("a"):

        href = link.get("href")
        text = link.get_text(" ", strip=True)

        if not href or not text:
            continue

        href_lower = href.lower()

        if not (
            href_lower.endswith(".pdf")
            or href_lower.endswith(".zip")
        ):
            continue

        if href.startswith("/"):
            href = f"{CGIBS_URL}{href}"

        file_type = (
            "PDF"
            if href_lower.endswith(".pdf")
            else "ZIP"
        )

        files.append({
            "title": text,
            "file_type": file_type,
            "url": href
        })

    return files


def inspect_cgibs_dere_dates(url: str) -> list[str]:

    html = fetch_cgibs_document_page(url)

    soup = BeautifulSoup(html, "html.parser")

    texts = []

    for element in soup.find_all(
        ["time", "span", "p", "small", "div"]
    ):
        text = element.get_text(" ", strip=True)

        if not text:
            continue

        text_lower = text.lower()

        if (
            "publicado" in text_lower
            or "atualizado" in text_lower
            or "alterado" in text_lower
            or "última atualização" in text_lower
            or "ultima atualização" in text_lower
        ):
            texts.append(text)

    return texts


def inspect_cgibs_dere_metadata(url: str) -> list[dict]:

    html = fetch_cgibs_document_page(url)

    soup = BeautifulSoup(html, "html.parser")

    metadata = []

    for meta in soup.find_all("meta"):

        name = (
            meta.get("name")
            or meta.get("property")
            or meta.get("itemprop")
        )

        content = meta.get("content")

        if name and content:
            metadata.append({
                "name": name,
                "content": content
            })

    return metadata


def parse_cgibs_document_metadata(url: str) -> dict:

    html = fetch_cgibs_document_page(url)

    soup = BeautifulSoup(html, "html.parser")

    title_element = soup.find(
        "meta",
        attrs={"property": "og:title"}
    )

    published_element = soup.find(
        "meta",
        attrs={"property": "article:published_time"}
    )

    modified_element = soup.find(
        "meta",
        attrs={"property": "article:modified_time"}
    )

    section_element = soup.find(
        "meta",
        attrs={"property": "article:section"}
    )

    return {
        "title": (
            title_element.get("content")
            if title_element
            else None
        ),
        "published_at": (
            datetime.fromisoformat(
                published_element.get("content")
            )
            if published_element
            else None
        ),
        "modified_at": (
            datetime.fromisoformat(
                modified_element.get("content")
            )
            if modified_element
            else None
        ),
        "section": (
            section_element.get("content")
            if section_element
            else None
        ),
        "url": url
    }


def parse_cgibs_document_publication(url: str) -> Publication:

    metadata = parse_cgibs_document_metadata(url)

    external_id = generate_external_id(
        source="CGIBS",
        source_identifier=url
    )

    return Publication(
        external_id=external_id,
        source="CGIBS",
        title=metadata["title"],
        document_type="DOCUMENTO_TECNICO",
        published_at=metadata["published_at"],
        modified_at=metadata["modified_at"],
        description=metadata["section"],
        download_url=url
    )


def get_cgibs_technical_publications() -> list[Publication]:

    data = fetch_cgibs_technical_documents_api()

    documents = parse_cgibs_technical_documents(
        data.get("body", "")
    )

    publications = []

    for document in documents:

        publication = parse_cgibs_document_publication(
            document["url"]
        )

        publications.append(publication)

    return publications
