import httpx


from bs4 import BeautifulSoup
from datetime import datetime
from urllib.parse import urljoin
from app.model.publication import Publication
from app.service.publication_identity_service import generate_external_id


NFE_PORTAL_URL = "https://www.nfe.fazenda.gov.br/portal/principal.aspx"


def fetch_nfe_portal_page() -> str:

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/153.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "pt-BR,pt;q=0.9"
    }

    response = httpx.get(
        NFE_PORTAL_URL,
        headers=headers,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()


NFE_NOTAS_TECNICAS_URL = (
    "https://www.nfe.fazenda.gov.br/portal/"
    "listaConteudo.aspx?tipoConteudo=04BIflQt1aY="
)


def fetch_nfe_notas_tecnicas_page() -> str:

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/153.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "pt-BR,pt;q=0.9"
    }

    response = httpx.get(
        NFE_NOTAS_TECNICAS_URL,
        headers=headers,
        timeout=30.0,
        follow_redirects=True
    )

    response.raise_for_status()

    return response.text


def parse_nfe_notas_tecnicas_links(html: str) -> list[dict]:

    soup = BeautifulSoup(html, "html.parser")

    notas = []

    for link in soup.find_all("a", href=True):

        text = link.get_text(" ", strip=True)
        href = link.get("href")

        if "nota técnica" in text.lower() or "nt " in text.lower():

            notas.append({
                "title": text,
                "href": href
            })

    return notas


def parse_nfe_publications(notas: list[dict]) -> list[Publication]:

    publications = []

    for nota in notas:

        title = nota["title"]
        href = nota["href"]

        if "Publicada em " not in title:
            continue

        date_text = title.split("Publicada em ")[1][:10]

        published_at = datetime.strptime(
            date_text,
            "%d/%m/%Y"
        )

        download_url = urljoin(
            NFE_PORTAL_URL,
            href
        )

        external_id = generate_external_id(
            source="PORTAL_NFE",
            source_identifier=download_url
        )

        publication = Publication(
            external_id=external_id,
            source="PORTAL_NFE",
            title=title,
            document_type="NOTA_TECNICA",
            published_at=published_at,
            modified_at=None,
            description=None,
            download_url=download_url
        )

        publications.append(publication)

    return publications
