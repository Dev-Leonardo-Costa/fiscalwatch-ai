import httpx

from bs4 import BeautifulSoup
from datetime import datetime
from app.model.publication import Publication
from datetime import datetime, timedelta
from app.service.publication_identity_service import generate_external_id


RECEITA_RTC_URL = (
    "https://www.gov.br/receitafederal/pt-br/"
    "acesso-a-informacao/acoes-e-programas/"
    "programas-e-atividades/reforma-tributaria-do-consumo"
)


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
    soup = BeautifulSoup(html, "html.parser")

    title_element = soup.find("h1")

    published_element = soup.select_one(
        "span.documentPublished"
    )

    content_element = soup.select_one(
        "div[property='rnews:articleBody']"
    )

    published_at = None

    if published_element:
        published_text = published_element.get_text(
            " ",
            strip=True
        )

        published_text = published_text.replace(
            "Publicado em ",
            ""
        )

        published_at = datetime.strptime(
            published_text,
            "%d/%m/%Y %Hh%M"
        )

    return {
        "title": (
            title_element.get_text(" ", strip=True)
            if title_element
            else None
        ),
        "published_at": published_at,
        "content": (
            content_element.get_text(" ", strip=True)
            if content_element
            else None
        )
    }


def parse_news_publication(url: str) -> Publication:

    data = inspect_news_page(url)

    external_id = generate_external_id(
        source="RECEITA_FEDERAL",
        source_identifier=url
    )

    return Publication(
        external_id=external_id,
        source="RECEITA_FEDERAL",
        title=data["title"],
        document_type="NOTICIA",
        published_at=data["published_at"],
        description=data["content"],
        download_url=url
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


