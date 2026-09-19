import re
import httpx
import re

from bs4 import BeautifulSoup
from urllib.parse import urlencode
from datetime import datetime
from app.model.publication import Publication
from datetime import datetime, timedelta
from pypdf import PdfReader


SVRS_URL = "https://dfe-portal.svrs.rs.gov.br/NFe/Documentos"
SVRS_BASE_URL = "https://dfe-portal.svrs.rs.gov.br"


def fetch_svrs_page():
    response = httpx.get(
        SVRS_URL,
        timeout=30.0
    )

    response.raise_for_status()

    return response.text


def parse_svrs_page(html: str):
    soup = BeautifulSoup(html, "html.parser")

    links = []

    for link in soup.find_all("a"):
        text = link.get_text(" ", strip=True)
        href = link.get("href")

        if text:
            links.append({
                "text": text,
                "href": href
            })

    return links


def inspect_document(html: str, search_text: str):
    soup = BeautifulSoup(html, "html.parser")

    for link in soup.find_all("a"):
        text = link.get_text(" ", strip=True)

        if search_text.lower() in text.lower():
            return {
                "text": text,
                "href": link.get("href"),
                "onclick": link.get("onclick")
            }

    return None


def find_download_script(html: str):
    soup = BeautifulSoup(html, "html.parser")

    for script in soup.find_all("script"):
        content = script.string or script.get_text()

        if "download_arquivo_estatico" in content:
            return content

    return None


def build_download_url(onclick: str):
    if not onclick:
        return None

    pattern = (
        r"download_arquivo_estatico\("
        r"'([^']+)',\s*"
        r"(\d+),\s*"
        r"'([^']+)'\)"
    )

    match = re.search(pattern, onclick)

    if not match:
        return None

    sistema = match.group(1)
    tipo = match.group(2)
    nome = match.group(3)

    params = urlencode({
        "sistema": sistema,
        "tipoArquivo": tipo,
        "nomeArquivo": nome
    })

    return (
        f"{SVRS_BASE_URL}/{sistema}/DownloadArquivoEstatico/"
        f"?{params}"
    )


def inspect_publication_container(html: str, search_text: str):
    soup = BeautifulSoup(html, "html.parser")

    for link in soup.find_all("a"):
        text = link.get_text(" ", strip=True)

        if search_text.lower() in text.lower():
            container = link.find_parent(
                class_="conteudo-lista__item"
            )

            if container:
                return str(container)

            return str(link.parent)

    return None


def get_publications() -> list[Publication]:
    html = fetch_svrs_page()
    soup = BeautifulSoup(html, "html.parser")
    articles = soup.select("article.conteudo-lista__item")
    publications = []

    for article in articles:
        link = article.select_one(
            "h2.conteudo-lista__item__titulo a"
        )

        time_element = article.select_one(
            "time.conteudo-lista__item__datahora"
        )

        if not link or not time_element:
            continue

        title = link.get_text(
            " ",
            strip=True
        )

        date_text = time_element.get_text(
            " ",
            strip=True
        )

        published_at = datetime.strptime(
            date_text,
            "%d/%m/%Y"
        ).date()

        description_element = article.find("p")

        description = None

        if description_element:
            description = description_element.get_text(
                " ",
                strip=True
            )

        onclick = link.get("onclick")

        download_url = build_download_url(
            onclick
        )

        publication = Publication(
            source="SVRS",
            title=title,
            document_type=classify_document_type(title),
            published_at=published_at,
            description=description,
            download_url=download_url
        )

        publications.append(publication)

    return publications


def classify_document_type(title: str) -> str:
    title_lower = title.lower()

    # Primeiro verificamos os tipos mais específicos
    if "schema" in title_lower:
        return "SCHEMA"

    if "manual" in title_lower:
        return "MANUAL"

    if "tabela" in title_lower:
        return "TABELA"

    if (
        "nota técnica" in title_lower
        or title_lower.startswith("nt ")
        or title_lower.startswith("nt20")
        or " nota técnica " in title_lower
    ):
        return "NOTA_TECNICA"

    return "OUTRO"  


def filter_recent_publications(
    publications: list[Publication],
    hours: int = 72
) -> list[Publication]:

    limit_date = (
        datetime.now() - timedelta(hours=hours)
    ).date()

    recent_publications = []

    for publication in publications:
        if publication.published_at >= limit_date:
            recent_publications.append(publication)

    return recent_publications  


from pathlib import Path
import httpx


def download_document(
    url: str,
    filename: str
) -> str:

    downloads_dir = Path("downloads")
    downloads_dir.mkdir(exist_ok=True)

    file_path = downloads_dir / filename

    response = httpx.get(
        url,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    file_path.write_bytes(response.content)

    return str(file_path)


def extract_pdf_text(file_path: str) -> str:
    reader = PdfReader(file_path)

    pages_text = []

    for page in reader.pages:
        text = page.extract_text()

        if text:
            pages_text.append(text)

    return "\n".join(pages_text)


def normalize_text(text: str) -> str:
    # Remove espaços e tabs repetidos
    text = re.sub(r"[ \t]+", " ", text)

    # Remove espaços antes das quebras de linha
    text = re.sub(r" *\n *", "\n", text)

    # Evita várias linhas vazias seguidas
    text = re.sub(r"\n{3,}", "\n\n", text)

    return text.strip()