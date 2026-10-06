import hashlib
from datetime import datetime
from pathlib import Path

from app.collectors import cgibs_collector
from app.model.publication import Publication
from app.service import document_extraction_service


def test_deve_extrair_texto_e_calcular_hash_sha256(monkeypatch, tmp_path):
    pdf_path = tmp_path / "documento.pdf"
    pdf_path.write_bytes(b"%PDF")

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(pdf_path)
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "extract_pdf_text",
        lambda file_path: " Texto   extraido \n\n\n com sucesso "
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "normalize_text",
        lambda text: "Texto extraido\ncom sucesso"
    )

    publication = criar_publicacao(
        download_url="https://example.com/documento.pdf"
    )

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "EXTRACTED"
    assert document.source_url == publication.download_url
    assert document.content_text == "Texto extraido\ncom sucesso"
    assert document.content_length == len("Texto extraido\ncom sucesso")
    assert document.content_hash == hashlib.sha256(
        "Texto extraido\ncom sucesso".encode("utf-8")
    ).hexdigest()
    assert document.extraction_error is None
    assert document.extractor_version == "svrs-pypdf-v1"
    assert document.extracted_at is not None
    assert pdf_path.exists() is False


def test_deve_retornar_pending_quando_nao_ha_download_url():
    publication = criar_publicacao(download_url=None)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "PENDING"
    assert document.source_url is None
    assert document.content_text is None
    assert document.content_hash is None
    assert document.content_length is None
    assert document.extraction_error is None


def test_deve_retornar_empty_quando_texto_normalizado_estiver_vazio(
    monkeypatch,
    tmp_path
):
    pdf_path = tmp_path / "documento-vazio.pdf"
    pdf_path.write_bytes(b"%PDF")

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(pdf_path)
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "extract_pdf_text",
        lambda file_path: "     "
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "normalize_text",
        lambda text: ""
    )

    publication = criar_publicacao(
        download_url="https://example.com/vazio.pdf"
    )

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "EMPTY"
    assert document.content_text == ""
    assert document.content_hash is None
    assert document.content_length == 0
    assert document.extraction_error is None
    assert pdf_path.exists() is False


def test_deve_retornar_failed_quando_download_falhar(monkeypatch):
    def falhar_download(url, filename):
        raise RuntimeError("download indisponivel\nstack trace omitido")

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        falhar_download
    )

    publication = criar_publicacao(
        download_url="https://example.com/falha.pdf"
    )

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "FAILED"
    assert document.content_text is None
    assert document.content_hash is None
    assert document.content_length is None
    assert document.extraction_error == "download indisponivel"
    assert "\n" not in document.extraction_error


def test_deve_retornar_failed_quando_extracao_falhar(
    monkeypatch,
    tmp_path
):
    pdf_path = tmp_path / "documento.pdf"
    pdf_path.write_bytes(b"%PDF")

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(pdf_path)
    )

    def falhar_extracao(file_path):
        raise ValueError("pdf invalido")

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "extract_pdf_text",
        falhar_extracao
    )

    publication = criar_publicacao(
        download_url="https://example.com/invalido.pdf"
    )

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "FAILED"
    assert document.extraction_error == "pdf invalido"
    assert pdf_path.exists() is False


def test_deve_extrair_html_da_receita_sem_usar_fluxo_pdf(monkeypatch):
    publication = Publication(
        external_id="b" * 64,
        source="RECEITA_FEDERAL",
        title="Receita Federal publica nova documentacao tecnica",
        document_type="NOTICIA",
        published_at=datetime.now(),
        download_url="https://www.gov.br/receitafederal/noticia"
    )

    monkeypatch.setattr(
        document_extraction_service.receita_collector,
        "extract_news_document",
        lambda url: document_extraction_service.PublicationDocument(
            source_url=url,
            content_text="Texto HTML normalizado",
            content_hash=hashlib.sha256(
                "Texto HTML normalizado".encode("utf-8")
            ).hexdigest(),
            content_length=len("Texto HTML normalizado"),
            extraction_status="EXTRACTED",
            extractor_version="receita-html-v1",
            extracted_at=datetime.now()
        )
    )

    def falhar_se_usar_pdf(_url, _filename):
        raise AssertionError("Fluxo PDF nao deve ser usado para Receita")

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        falhar_se_usar_pdf
    )

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "EXTRACTED"
    assert document.content_text == "Texto HTML normalizado"
    assert document.extractor_version == "receita-html-v1"


def test_deve_gerar_publication_document_extraido_para_cgibs(
    monkeypatch,
    tmp_path
):
    aggregator_url = (
        "https://www.cgibs.gov.br/declaracao-de-regimes-especificos-dere"
    )
    pdf_url = (
        "https://www.cgibs.gov.br/upload/arquivos/"
        "04-regras-de-validacao-v-1-3-0.pdf"
    )
    extracted_text = " Texto   extraido \n\n\n das regras "
    normalized_text = "Texto extraido\ndas regras"
    pdf_path = tmp_path / "regras-validacao.pdf"
    pdf_path.write_bytes(b"%PDF")

    monkeypatch.setattr(
        cgibs_collector,
        "parse_cgibs_technical_files",
        lambda url: [
            {
                "title": (
                    "04 Leiautes da DeRE Anexo II "
                    "Regras de Validação (v 1 3 0)"
                ),
                "file_type": "PDF",
                "url": pdf_url
            }
        ]
    )
    monkeypatch.setattr(
        cgibs_collector,
        "select_cgibs_main_technical_file",
        lambda files: files[0]
    )

    def baixar_pdf(url, filename):
        assert url == pdf_url
        return str(pdf_path)

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        baixar_pdf
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "extract_pdf_text",
        lambda file_path: extracted_text
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "normalize_text",
        lambda text: normalized_text
    )

    publication = criar_publicacao_cgibs(download_url=aggregator_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "EXTRACTED"
    assert document.source_url == pdf_url
    assert document.content_text == normalized_text
    assert document.content_hash == hashlib.sha256(
        normalized_text.encode("utf-8")
    ).hexdigest()
    assert document.content_length == len(normalized_text)
    assert document.extraction_error is None
    assert document.extractor_version == "cgibs-pypdf-v1"
    assert document.extracted_at is not None
    assert pdf_path.exists() is False


def test_deve_retornar_pending_para_cgibs_sem_pdf_tecnico_adequado(
    monkeypatch
):
    aggregator_url = (
        "https://www.cgibs.gov.br/declaracao-de-regimes-especificos-dere"
    )

    monkeypatch.setattr(
        cgibs_collector,
        "parse_cgibs_technical_files",
        lambda url: [
            {
                "title": "06 Arquivos XSD Regras de Validação (v 1 3 0)",
                "file_type": "ZIP",
                "url": "https://www.cgibs.gov.br/upload/xsd.zip"
            }
        ]
    )
    monkeypatch.setattr(
        cgibs_collector,
        "select_cgibs_main_technical_file",
        lambda files: None
    )

    def falhar_se_tentar_baixar(_url, _filename):
        raise AssertionError("Nao deve baixar arquivo sem PDF selecionado")

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        falhar_se_tentar_baixar
    )

    publication = criar_publicacao_cgibs(download_url=aggregator_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "PENDING"
    assert document.source_url is None
    assert document.content_text is None
    assert document.content_hash is None
    assert document.content_length is None
    assert document.extraction_error is None


def test_deve_retornar_failed_para_cgibs_quando_download_ou_extracao_falhar(
    monkeypatch
):
    aggregator_url = (
        "https://www.cgibs.gov.br/declaracao-de-regimes-especificos-dere"
    )
    pdf_url = (
        "https://www.cgibs.gov.br/upload/arquivos/"
        "04-regras-de-validacao-v-1-3-0.pdf"
    )

    monkeypatch.setattr(
        cgibs_collector,
        "parse_cgibs_technical_files",
        lambda url: [
            {
                "title": (
                    "04 Leiautes da DeRE Anexo II "
                    "Regras de Validação (v 1 3 0)"
                ),
                "file_type": "PDF",
                "url": pdf_url
            }
        ]
    )
    monkeypatch.setattr(
        cgibs_collector,
        "select_cgibs_main_technical_file",
        lambda files: files[0]
    )

    def falhar_download(url, filename):
        assert url == pdf_url
        raise RuntimeError("download cgibs indisponivel\nstack trace")

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        falhar_download
    )

    publication = criar_publicacao_cgibs(download_url=aggregator_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "FAILED"
    assert document.source_url == pdf_url
    assert document.content_text is None
    assert document.content_hash is None
    assert document.content_length is None
    assert document.extraction_error == "download cgibs indisponivel"
    assert document.extractor_version == "cgibs-pypdf-v1"
    assert document.extracted_at is not None


def test_deve_gerar_publication_document_extraido_para_portal_nfe(
    monkeypatch,
    tmp_path
):
    pdf_url = (
        "https://www.nfe.fazenda.gov.br/portal/"
        "exibirArquivo.aspx?conteudo=abc"
    )
    extracted_text = " Texto   extraido \n\n\n da NF-e "
    normalized_text = "Texto extraido\nda NF-e"
    pdf_path = tmp_path / "nota-tecnica-nfe.pdf"
    pdf_path.write_bytes(b"%PDF")

    def baixar_pdf(url, filename):
        assert url == pdf_url
        return str(pdf_path)

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        baixar_pdf
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "extract_pdf_text",
        lambda file_path: extracted_text
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "normalize_text",
        lambda text: normalized_text
    )

    publication = criar_publicacao_portal_nfe(download_url=pdf_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "EXTRACTED"
    assert document.source_url == pdf_url
    assert document.content_text == normalized_text
    assert document.content_hash == hashlib.sha256(
        normalized_text.encode("utf-8")
    ).hexdigest()
    assert document.content_length == len(normalized_text)
    assert document.extraction_error is None
    assert document.extractor_version == "portal-nfe-pypdf-v1"
    assert document.extracted_at is not None
    assert pdf_path.exists() is False


def test_deve_retornar_pending_para_portal_nfe_sem_download_url():
    publication = criar_publicacao_portal_nfe(download_url=None)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "PENDING"
    assert document.source_url is None
    assert document.content_text is None
    assert document.content_hash is None
    assert document.content_length is None
    assert document.extraction_error is None


def test_deve_retornar_failed_para_portal_nfe_quando_download_ou_extracao_falhar(
    monkeypatch
):
    pdf_url = (
        "https://www.nfe.fazenda.gov.br/portal/"
        "exibirArquivo.aspx?conteudo=erro"
    )

    def falhar_download(url, filename):
        assert url == pdf_url
        raise RuntimeError("download nfe indisponivel\nstack trace")

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        falhar_download
    )

    publication = criar_publicacao_portal_nfe(download_url=pdf_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "FAILED"
    assert document.source_url == pdf_url
    assert document.content_text is None
    assert document.content_hash is None
    assert document.content_length is None
    assert document.extraction_error == "download nfe indisponivel"
    assert "\n" not in document.extraction_error
    assert document.extractor_version == "portal-nfe-pypdf-v1"
    assert document.extracted_at is not None


def criar_publicacao(download_url: str | None) -> Publication:
    return Publication(
        external_id="a" * 64,
        source="SVRS",
        title="Nota Técnica 2026.009 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        download_url=download_url
    )


def criar_publicacao_portal_nfe(download_url: str | None) -> Publication:
    return Publication(
        external_id="d" * 64,
        source="PORTAL_NFE",
        title="Nota Técnica 2026.001 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        download_url=download_url
    )


def criar_publicacao_cgibs(download_url: str | None) -> Publication:
    return Publication(
        external_id="c" * 64,
        source="CGIBS",
        title="Declaração de Regimes Específicos (DeRE)",
        document_type="DOCUMENTO_TECNICO",
        published_at=datetime.now(),
        download_url=download_url
    )
