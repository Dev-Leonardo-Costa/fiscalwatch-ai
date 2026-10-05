import json
from datetime import datetime

from app.collectors import cgibs_collector


CGIBS_DOCUMENT_HTML = """
<html>
    <head>
        <meta property="og:title"
              content="Declaração de Regimes Específicos (DeRE)" />
        <meta property="article:published_time"
              content="2026-06-22T14:59:00-03:00" />
        <meta property="article:modified_time"
              content="2026-10-05T14:58:31-03:00" />
        <meta property="article:section"
              content="Documentos Técnicos" />
    </head>
    <body></body>
</html>
"""

CGIBS_DERE_URL = (
    "https://www.cgibs.gov.br/declaracao-de-regimes-especificos-dere"
)


def _parse_cgibs_files_from_html(monkeypatch, html: str) -> list[dict]:
    monkeypatch.setattr(
        cgibs_collector,
        "fetch_cgibs_document_page",
        lambda url: html
    )

    return cgibs_collector.parse_cgibs_technical_files(CGIBS_DERE_URL)


def test_deve_normalizar_published_time_cgibs_sem_tzinfo(monkeypatch):
    monkeypatch.setattr(
        cgibs_collector,
        "fetch_cgibs_document_page",
        lambda url: CGIBS_DOCUMENT_HTML
    )

    metadata = cgibs_collector.parse_cgibs_document_metadata(
        "https://www.cgibs.gov.br/declaracao-de-regimes-especificos-dere"
    )

    assert metadata["published_at"] == datetime(2026, 6, 22, 14, 59, 0)
    assert metadata["published_at"].tzinfo is None


def test_deve_normalizar_modified_time_cgibs_sem_tzinfo(monkeypatch):
    monkeypatch.setattr(
        cgibs_collector,
        "fetch_cgibs_document_page",
        lambda url: CGIBS_DOCUMENT_HTML
    )

    metadata = cgibs_collector.parse_cgibs_document_metadata(
        "https://www.cgibs.gov.br/declaracao-de-regimes-especificos-dere"
    )

    assert metadata["modified_at"] == datetime(2026, 10, 5, 14, 58, 31)
    assert metadata["modified_at"].tzinfo is None


def test_publication_cgibs_deve_ter_datas_sem_tzinfo(monkeypatch):
    monkeypatch.setattr(
        cgibs_collector,
        "fetch_cgibs_document_page",
        lambda url: CGIBS_DOCUMENT_HTML
    )

    publication = cgibs_collector.parse_cgibs_document_publication(
        "https://www.cgibs.gov.br/declaracao-de-regimes-especificos-dere"
    )

    assert publication.published_at == datetime(2026, 6, 22, 14, 59, 0)
    assert publication.published_at.tzinfo is None
    assert publication.modified_at == datetime(2026, 10, 5, 14, 58, 31)
    assert publication.modified_at.tzinfo is None


def test_json_publication_cgibs_deve_serializar_datas_sem_offset(
    monkeypatch
):
    monkeypatch.setattr(
        cgibs_collector,
        "fetch_cgibs_document_page",
        lambda url: CGIBS_DOCUMENT_HTML
    )

    publication = cgibs_collector.parse_cgibs_document_publication(
        "https://www.cgibs.gov.br/declaracao-de-regimes-especificos-dere"
    )

    payload = json.loads(publication.model_dump_json())

    assert payload["published_at"] == "2026-06-22T14:59:00"
    assert payload["modified_at"] == "2026-10-05T14:58:31"
    assert payload["published_at"] != "2026-06-22T14:59:00-03:00"


def test_deve_selecionar_pdf_de_regras_de_validacao(monkeypatch):
    html = """
    <html>
        <body>
            <h4>DeRE - Versão atual</h4>
            <a href="/upload/02-leiautes-da-dere-eventos-v-1-3-0.pdf">
                02 Leiautes da DeRE Eventos (v 1 3 0)
            </a>
            <a href="/upload/03-leiautes-da-dere-anexo-i-tabelas-v-1-3-0.pdf">
                03 Leiautes da DeRE Anexo I Tabelas (v 1 3 0)
            </a>
            <a href="/upload/04-leiautes-da-dere-anexo-ii-regras-v-1-3-0.pdf">
                04 Leiautes da DeRE Anexo II Regras de Validação (v 1 3 0)
            </a>
            <a href="/upload/06-arquivos-xsd-producao.zip">
                06 Arquivos XSD (PRODUÇÃO)
            </a>
        </body>
    </html>
    """

    files = _parse_cgibs_files_from_html(monkeypatch, html)

    selected_file = cgibs_collector.select_cgibs_main_technical_file(files)

    assert selected_file["title"] == (
        "04 Leiautes da DeRE Anexo II Regras de Validação (v 1 3 0)"
    )
    assert selected_file["file_type"] == "PDF"


def test_deve_ignorar_zip_xsd_mesmo_com_termos_tecnicos_relevantes(
    monkeypatch
):
    html = """
    <html>
        <body>
            <h4>DeRE - Versão atual</h4>
            <a href="/upload/04-regras-de-validacao-xsd-v-1-4-0.zip">
                04 Arquivos XSD Regras de Validação (v 1 4 0)
            </a>
            <a href="/upload/04-leiautes-regras-validacao-v-1-3-0.pdf">
                04 Leiautes da DeRE Anexo II Regras de Validação (v 1 3 0)
            </a>
        </body>
    </html>
    """

    files = _parse_cgibs_files_from_html(monkeypatch, html)

    selected_file = cgibs_collector.select_cgibs_main_technical_file(files)

    assert selected_file["title"] == (
        "04 Leiautes da DeRE Anexo II Regras de Validação (v 1 3 0)"
    )
    assert selected_file["file_type"] == "PDF"
    assert not selected_file["url"].endswith(".zip")


def test_deve_selecionar_pdf_regras_de_validacao_mais_recente(
    monkeypatch
):
    html = """
    <html>
        <body>
            <h4>DeRE - Versão atual</h4>
            <a href="/upload/04-leiautes-regras-validacao-v-1-2-0.pdf">
                04 Leiautes da DeRE Anexo II Regras de Validação (v 1 2 0)
            </a>
            <a href="/upload/04-leiautes-regras-validacao-v-1-3-0.pdf">
                04 Leiautes da DeRE Anexo II Regras de Validação (v 1 3 0)
            </a>
        </body>
    </html>
    """

    files = _parse_cgibs_files_from_html(monkeypatch, html)

    selected_file = cgibs_collector.select_cgibs_main_technical_file(files)

    assert selected_file["title"] == (
        "04 Leiautes da DeRE Anexo II Regras de Validação (v 1 3 0)"
    )


def test_deve_retornar_ausencia_quando_nao_existir_pdf_adequado(
    monkeypatch
):
    html = """
    <html>
        <body>
            <h4>DeRE - Versão atual</h4>
            <a href="/upload/02-leiautes-da-dere-eventos-v-1-3-0.pdf">
                02 Leiautes da DeRE Eventos (v 1 3 0)
            </a>
            <a href="/upload/03-leiautes-da-dere-anexo-i-tabelas-v-1-3-0.pdf">
                03 Leiautes da DeRE Anexo I Tabelas (v 1 3 0)
            </a>
            <a href="/upload/06-arquivos-xsd-regras-validacao-v-1-3-0.zip">
                06 Arquivos XSD Regras de Validação (v 1 3 0)
            </a>
        </body>
    </html>
    """

    files = _parse_cgibs_files_from_html(monkeypatch, html)

    selected_file = cgibs_collector.select_cgibs_main_technical_file(files)

    assert selected_file is None
