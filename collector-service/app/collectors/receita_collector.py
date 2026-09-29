import hashlib
import re
from urllib.parse import urldefrag

import httpx

from bs4 import BeautifulSoup
from datetime import datetime
from app.model.publication import Publication
from app.model.publication_document import PublicationDocument
from app.service.publication_identity_service import generate_external_id


RECEITA_RTC_URL = (
    "https://www.gov.br/receitafederal/pt-br/"
    "acesso-a-informacao/acoes-e-programas/"
    "programas-e-atividades/reforma-tributaria-do-consumo"
)
SOURCE = "RECEITA_FEDERAL"
HTML_EXTRACTOR_VERSION = "receita-html-v1"


def fetch_receita_page() -> str:
    response = httpx.get(
        RECEITA_RTC_URL,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def parse_receita_links(html: str):
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


def filter_relevant_links(links: list[dict]) -> list[dict]:

    keywords = [
        "reforma tributária",
        "reforma tributaria",
        "cbs",
        "ibs",
        "documentos fiscais",
        "nota técnica",
        "nota tecnica",
        "documentação técnica",
        "documentacao tecnica",
        "orientação conjunta",
        "orientacao conjunta"
    ]

    relevant_links = []
    seen_urls = set()

    for link in links:
        text = link["text"].lower()
        href = link["href"]

        if (
            any(keyword in text for keyword in keywords)
            and href not in seen_urls
        ):
            relevant_links.append(link)
            seen_urls.add(href)

    return relevant_links


def filter_news_links(links: list[dict]) -> list[dict]:

    news_links = []

    for link in links:
        href = link["href"]

        if "/assuntos/noticias/" in href:
            news_links.append(link)

    return news_links



def fetch_news_page(url: str) -> str:
    response = httpx.get(
        url,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def inspect_news_page(url: str) -> dict:

    html = fetch_news_page(url)

    return inspect_news_html(html)


def inspect_news_html(html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")

    title_element = soup.select_one("h1.documentFirstHeading")

    if not title_element:
        title_element = soup.find("h1")

    description = extract_description(soup)

    published_at = parse_govbr_datetime(
        soup.select_one("span.documentPublished"),
        "Publicado em "
    )

    modified_at = parse_govbr_datetime(
        soup.select_one("span.documentModified"),
        "Atualizado em "
    )

    content_text = extract_main_content_text(soup)

    return {
        "title": (
            normalize_text(title_element.get_text(" ", strip=True))
            if title_element
            else None
        ),
        "published_at": published_at,
        "modified_at": modified_at,
        "description": description,
        "content": content_text
    }


def parse_govbr_datetime(element, prefix: str):
    if not element:
        return None

    date_text = element.get_text(
            " ",
            strip=True
    )

    date_text = date_text.replace(
        prefix,
        ""
    ).strip()

    return datetime.strptime(
        date_text,
        "%d/%m/%Y %Hh%M"
    )


def extract_description(soup: BeautifulSoup) -> str | None:
    description_selectors = [
        "div.documentDescription",
        "p.documentDescription",
        "[property='rnews:description']",
        "meta[name='description']"
    ]

    for selector in description_selectors:
        element = soup.select_one(selector)

        if not element:
            continue

        if element.name == "meta":
            description = element.get("content")
        else:
            description = element.get_text(
                " ",
                strip=True
            )

        if description:
            return normalize_text(description)

    return None


def extract_main_content_text(soup: BeautifulSoup) -> str | None:
    content_selectors = [
        "div[property='rnews:articleBody']",
        "#parent-fieldname-text",
        "main article"
    ]

    for selector in content_selectors:
        element = soup.select_one(selector)

        if not element:
            continue

        text = normalize_text(
            element.get_text(
                "\n",
                strip=True
            )
        )

        if text:
            return text

    return None


def normalize_text(text: str) -> str:
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r" *\n *", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)

    return text.strip()


def canonicalize_url(url: str) -> str:
    url_without_fragment, _fragment = urldefrag(url)

    return url_without_fragment.strip()


def parse_news_publication_from_html(
    html: str,
    url: str
) -> Publication:

    canonical_url = canonicalize_url(url)
    data = inspect_news_html(html)

    external_id = generate_external_id(
        source=SOURCE,
        source_identifier=canonical_url
    )

    return Publication(
        external_id=external_id,
        source=SOURCE,
        title=data["title"],
        document_type="NOTICIA",
        published_at=data["published_at"],
        modified_at=data["modified_at"],
        description=data["description"],
        download_url=canonical_url
    )


def build_publication_document_from_html(
    html: str,
    source_url: str
) -> PublicationDocument:

    soup = BeautifulSoup(html, "html.parser")
    content_text = extract_main_content_text(soup)
    extracted_at = datetime.now()

    if not content_text:
        return PublicationDocument(
            source_url=canonicalize_url(source_url),
            content_text="",
            content_length=0,
            extraction_status="EMPTY",
            extraction_error=None,
            extractor_version=HTML_EXTRACTOR_VERSION,
            extracted_at=extracted_at
        )

    return PublicationDocument(
        source_url=canonicalize_url(source_url),
        content_text=content_text,
        content_hash=hashlib.sha256(
            content_text.encode("utf-8")
        ).hexdigest(),
        content_length=len(content_text),
        extraction_status="EXTRACTED",
        extraction_error=None,
        extractor_version=HTML_EXTRACTOR_VERSION,
        extracted_at=extracted_at
    )


def extract_news_document(url: str) -> PublicationDocument:
    canonical_url = canonicalize_url(url)
    html = fetch_news_page(canonical_url)

    return build_publication_document_from_html(
        html=html,
        source_url=canonical_url
    )


def parse_news_publication(url: str) -> Publication:

    html = fetch_news_page(url)

    return parse_news_publication_from_html(
        html=html,
        url=url
    )


def get_news_publications() -> list[Publication]:

    html = fetch_receita_page()

    links = parse_receita_links(html)

    relevant_links = filter_relevant_links(links)

    news_links = filter_news_links(relevant_links)

    publications = []

    for link in news_links:
        publication = parse_news_publication(
            link["href"]
        )

        publications.append(publication)

    return publications    


