import httpx
import json

from datetime import datetime
from app.model.publication import Publication
from bs4 import BeautifulSoup


IMPRENSA_NACIONAL_URL = "https://www.gov.br/imprensanacional"


def fetch_imprensa_nacional_page() -> str:

    response = httpx.get(
        IMPRENSA_NACIONAL_URL,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def parse_imprensa_links(html: str) -> list[dict]:

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


IMPRENSA_DOU_URL = (
    "https://www.gov.br/imprensanacional/"
    "pt-br/servicos/diario-oficial-da-uniao"
)


def fetch_dou_page() -> str:

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/153.0.0.0 Safari/537.36"
        ),
        "Accept": (
            "text/html,application/xhtml+xml,"
            "application/xml;q=0.9,*/*;q=0.8"
        ),
        "Accept-Language": "pt-BR,pt;q=0.9,en;q=0.8"
    }

    response = httpx.get(
        IMPRENSA_DOU_URL,
        headers=headers,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


IMPRENSA_DOU_DATABASE_URL = (
    "https://www.in.gov.br/web/guest/"
    "acesso-a-informacao/dados-abertos/base-de-dados"
)


def fetch_dou_database_page() -> str:

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/153.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "pt-BR,pt;q=0.9"
    }

    response = httpx.get(
        IMPRENSA_DOU_DATABASE_URL,
        headers=headers,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def parse_dou_database_content(html: str) -> str:

    soup = BeautifulSoup(html, "html.parser")

    main = soup.find("main")

    if not main:
        return ""

    return main.get_text(
        " ",
        strip=True
    )


def fetch_dou_section_page(
    date: str,
    section: str = "dou1"
) -> str:

    url = "https://www.in.gov.br/leiturajornal"

    params = {
        "secao": section,
        "data": date
    }

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/153.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "pt-BR,pt;q=0.9"
    }

    response = httpx.get(
        url,
        params=params,
        headers=headers,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def parse_dou_section_links(html: str) -> list[dict]:

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


def find_dou_api_candidates(html: str) -> list[str]:

    soup = BeautifulSoup(html, "html.parser")

    candidates = []

    for script in soup.find_all("script"):
        content = script.get_text(" ", strip=True)

        if not content:
            continue

        content_lower = content.lower()

        if (
            "api" in content_lower
            or "leiturajornal" in content_lower
            or "materia" in content_lower
            or "jornal" in content_lower
        ):
            candidates.append(content)

    return candidates


DOU_SEARCH_URL = "https://www.in.gov.br/consulta/-/buscar/dou"


def search_dou(
    keyword: str,
    section: str = "do1"
) -> str:

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/153.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "pt-BR,pt;q=0.9"
    }

    params = {
        "q": keyword,
        "s": section,
        "exactDate": "all"
    }

    response = httpx.get(
        DOU_SEARCH_URL,
        params=params,
        headers=headers,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def extract_dou_search_params(html: str) -> str | None:

    soup = BeautifulSoup(html, "html.parser")

    element = soup.find(
        id="_br_com_seatecnologia_in_buscadou_BuscaDouPortlet_params"
    )

    if not element:
        return None

    return element.get_text(strip=True)


def parse_dou_search_results(html: str) -> list[dict]:

    soup = BeautifulSoup(html, "html.parser")

    element = soup.find(
        id="_br_com_seatecnologia_in_buscadou_BuscaDouPortlet_params"
    )

    if not element:
        return []

    data = json.loads(element.get_text(strip=True))

    return data.get("jsonArray", [])


def parse_dou_publications(results: list[dict]) -> list[Publication]:

    publications = []

    for result in results:

        published_at = datetime.strptime(
            result["pubDate"],
            "%d/%m/%Y"
        )

        url = (
            "https://www.in.gov.br/web/dou/-/"
            + result["urlTitle"]
        )

        publication = Publication(
            source="IMPRENSA_NACIONAL_DOU",
            title=result["title"],
            document_type=result.get("artType"),
            published_at=published_at,
            modified_at=None,
            description=result.get("content"),
            download_url=url
        )

        publications.append(publication)

    return publications
