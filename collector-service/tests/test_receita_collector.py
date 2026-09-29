import hashlib
from datetime import datetime
from pathlib import Path

from app.collectors import receita_collector
from app.service.publication_identity_service import generate_external_id


FIXTURES_DIR = Path(__file__).parent / "fixtures"
NEWS_URL = (
    "https://www.gov.br/receitafederal/pt-br/assuntos/noticias/"
    "2026/setembro/receita-federal-publica-nova-documentacao-"
    "tecnica-das-apis-de-apuracao-de-cbs#fragmento"
)
CANONICAL_NEWS_URL = NEWS_URL.split("#")[0]


def test_deve_montar_publication_de_noticia_receita_com_identidade_url():
    html = load_fixture("receita_news.html")

    publication = receita_collector.parse_news_publication_from_html(
        html=html,
        url=NEWS_URL
    )

    assert publication.title == (
        "Receita Federal publica nova documentacao tecnica das APIs "
        "de apuracao da CBS"
    )
    assert publication.published_at == datetime(2026, 9, 14, 9, 39)
    assert publication.modified_at == datetime(2026, 9, 14, 10, 32)
    assert publication.description == (
        "Nova versao permitira consultas incrementais, reduzindo o "
        "tempo de processamento."
    )
    assert publication.source == "RECEITA_FEDERAL"
    assert publication.document_type == "NOTICIA"
    assert publication.download_url == CANONICAL_NEWS_URL
    assert publication.external_id == generate_external_id(
        source="RECEITA_FEDERAL",
        source_identifier=CANONICAL_NEWS_URL
    )


def test_deve_extrair_documento_html_principal_normalizado():
    html = load_fixture("receita_news.html")

    document = receita_collector.build_publication_document_from_html(
        html=html,
        source_url=NEWS_URL
    )

    expected_text = (
        "A Receita Federal disponibilizara novas APIs gratuitas\n"
        "para consulta de informacoes relacionadas a CBS.\n"
        "Entre as novidades estao servicos para consulta de:\n"
        "debitos, creditos, pagamentos e recolhimentos.\n"
        "Recomenda-se atencao das equipes de desenvolvimento."
    )

    assert document.source_url == CANONICAL_NEWS_URL
    assert document.content_text == expected_text
    assert "Menu gov.br" not in document.content_text
    assert "Pagina Inicial" not in document.content_text
    assert "Conteudo lateral" not in document.content_text
    assert "Rodape gov.br" not in document.content_text
    assert document.content_hash == hashlib.sha256(
        expected_text.encode("utf-8")
    ).hexdigest()
    assert document.content_length == len(expected_text)
    assert document.extraction_status == "EXTRACTED"
    assert document.extraction_error is None
    assert document.extractor_version == "receita-html-v1"
    assert document.extracted_at is not None


def test_deve_usar_fallback_conservador_para_corpo_govbr_antigo():
    html = load_fixture("receita_news_fallback.html")

    document = receita_collector.build_publication_document_from_html(
        html=html,
        source_url=NEWS_URL
    )

    assert document.extraction_status == "EXTRACTED"
    assert document.content_text == (
        "Opcao pelo Regime Especifico do IBS e da CBS."
    )


def test_html_sem_conteudo_principal_nao_inventa_texto():
    html = load_fixture("receita_news_without_main_content.html")

    document = receita_collector.build_publication_document_from_html(
        html=html,
        source_url=NEWS_URL
    )

    assert document.extraction_status == "EMPTY"
    assert document.content_text == ""
    assert document.content_hash is None
    assert document.content_length == 0
    assert document.extraction_error is None


def test_data_de_atualizacao_nao_substitui_data_de_publicacao():
    html = load_fixture("receita_news_fallback.html")

    publication = receita_collector.parse_news_publication_from_html(
        html=html,
        url=NEWS_URL
    )

    assert publication.published_at == datetime(2026, 9, 18, 16, 48)
    assert publication.modified_at == datetime(2026, 9, 21, 9, 9)
    assert publication.published_at != publication.modified_at


def load_fixture(filename: str) -> str:
    return (FIXTURES_DIR / filename).read_text(encoding="utf-8")
