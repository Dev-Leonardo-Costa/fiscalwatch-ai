import hashlib
import zipfile
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


def test_deve_extrair_zip_svrs_com_xsd(monkeypatch, tmp_path):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "schemas/nfe_v4.00.xsd": (
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                "  <xs:element name=\"NFe\"/>\n"
                "</xs:schema>\n"
            )
        }
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "extract_pdf_text",
        lambda file_path: (_ for _ in ()).throw(
            AssertionError("ZIP de schema nao deve usar pypdf")
        )
    )

    publication = criar_publicacao_schema(
        download_url="https://example.com/schemas.zip"
    )

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "EXTRACTED"
    assert document.extractor_version == "svrs-schema-zip-v1"
    assert "=== arquivo: schemas/nfe_v4.00.xsd ===" in (
        document.content_text
    )
    assert "<xs:element name=\"NFe\"/>" in document.content_text
    assert document.content_length == len(document.content_text)
    assert document.content_hash == hashlib.sha256(
        document.content_text.encode("utf-8")
    ).hexdigest()


def test_deve_concatenar_multiplos_xsd_do_zip_svrs(monkeypatch, tmp_path):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "schemas/a.xsd": "<xs:schema>A</xs:schema>",
            "schemas/b.xsd": "<xs:schema>B</xs:schema>"
        }
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "EXTRACTED"
    assert "=== arquivo: schemas/a.xsd ===" in document.content_text
    assert "=== arquivo: schemas/b.xsd ===" in document.content_text
    assert "<xs:schema>A</xs:schema>" in document.content_text
    assert "<xs:schema>B</xs:schema>" in document.content_text


def test_deve_ordenar_arquivos_do_zip_svrs_por_caminho(monkeypatch, tmp_path):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "schemas/z.xsd": "<xs:schema>Z</xs:schema>",
            "schemas/a.xsd": "<xs:schema>A</xs:schema>",
            "schemas/m.xsd": "<xs:schema>M</xs:schema>"
        }
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.content_text.index("schemas/a.xsd") < (
        document.content_text.index("schemas/m.xsd")
    )
    assert document.content_text.index("schemas/m.xsd") < (
        document.content_text.index("schemas/z.xsd")
    )


def test_hash_do_zip_svrs_deve_ser_deterministico(monkeypatch, tmp_path):
    primeiro_zip = criar_zip_schema(
        tmp_path,
        {
            "schemas/b.xsd": "<xs:schema>B</xs:schema>",
            "schemas/a.xsd": "<xs:schema>A</xs:schema>"
        },
        filename="primeiro.zip"
    )
    segundo_zip = criar_zip_schema(
        tmp_path,
        {
            "schemas/a.xsd": "<xs:schema>A</xs:schema>",
            "schemas/b.xsd": "<xs:schema>B</xs:schema>"
        },
        filename="segundo.zip"
    )
    caminhos = iter([str(primeiro_zip), str(segundo_zip)])
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: next(caminhos)
    )
    publication = criar_publicacao_schema(
        download_url="https://example.com/schema.zip"
    )

    primeiro = document_extraction_service.extract_publication_document(
        publication
    )
    segundo = document_extraction_service.extract_publication_document(
        publication
    )

    assert primeiro.content_text == segundo.content_text
    assert primeiro.content_hash == segundo.content_hash
    assert primeiro.content_length == segundo.content_length


def test_zip_svrs_deve_aceitar_xsd_e_xml_case_insensitive(
    monkeypatch,
    tmp_path
):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "schemas/A.XSD": "<xs:schema>A</xs:schema>",
            "schemas/B.Xml": "<root>B</root>",
            "schemas/ignorado.txt": "ignorar"
        }
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert "schemas/A.XSD" in document.content_text
    assert "schemas/B.Xml" in document.content_text
    assert "ignorado.txt" not in document.content_text


def test_zip_svrs_sem_xsd_ou_xml_deve_retornar_empty(monkeypatch, tmp_path):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "schemas/readme.txt": "sem schema"
        }
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "EMPTY"
    assert document.content_text == ""
    assert document.content_length == 0
    assert document.content_hash is None
    assert document.extractor_version == "svrs-schema-zip-v1"


def test_zip_svrs_corrompido_deve_retornar_failed(monkeypatch, tmp_path):
    zip_path = tmp_path / "corrompido.zip"
    zip_path.write_bytes(b"PK\x03\x04conteudo invalido")
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "FAILED"
    assert document.extractor_version == "svrs-schema-zip-v1"
    assert document.extraction_error is not None


def test_zip_svrs_deve_bloquear_path_traversal(monkeypatch, tmp_path):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "../arquivo.xsd": "<xs:schema/>"
        }
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "FAILED"
    assert "caminho inseguro" in document.extraction_error


def test_zip_svrs_deve_bloquear_path_traversal_com_barra_invertida(
    monkeypatch,
    tmp_path
):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "..\\arquivo.xsd": "<xs:schema/>"
        }
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "FAILED"
    assert "caminho inseguro" in document.extraction_error


def test_zip_svrs_deve_bloquear_caminho_absoluto(monkeypatch, tmp_path):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "/arquivo.xsd": "<xs:schema/>"
        }
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "FAILED"
    assert "caminho inseguro" in document.extraction_error


def test_zip_svrs_deve_bloquear_quantidade_excessiva_de_arquivos(
    monkeypatch,
    tmp_path
):
    arquivos = {
        f"schemas/{indice}.xsd": "<xs:schema/>"
        for indice in range(
            document_extraction_service.SVRS_SCHEMA_ZIP_MAX_FILES + 1
        )
    }
    zip_path = criar_zip_schema(tmp_path, arquivos)
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "FAILED"
    assert "quantidade de arquivos excede o limite" in (
        document.extraction_error
    )


def test_zip_svrs_deve_bloquear_tamanho_total_excessivo(
    monkeypatch,
    tmp_path
):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "schemas/grande.xsd": "A" * (
                document_extraction_service
                .SVRS_SCHEMA_ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES + 1
            )
        },
        compression=zipfile.ZIP_STORED
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "FAILED"
    assert "tamanho total descompactado excede o limite" in (
        document.extraction_error
    )


def test_zip_svrs_deve_bloquear_arquivo_individual_excessivo(
    monkeypatch,
    tmp_path
):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "schemas/grande.xsd": "A" * (
                document_extraction_service
                .SVRS_SCHEMA_ZIP_MAX_FILE_UNCOMPRESSED_BYTES + 1
            )
        },
        compression=zipfile.ZIP_STORED
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "FAILED"
    assert "arquivo interno excede o limite" in document.extraction_error


def test_zip_svrs_deve_bloquear_razao_de_compressao_anormal(
    monkeypatch,
    tmp_path
):
    zip_path = criar_zip_schema(
        tmp_path,
        {
            "schemas/bomba.xsd": "A" * 200_000
        },
        compression=zipfile.ZIP_DEFLATED
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(zip_path)
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao_schema(download_url="https://example.com/schema.zip")
    )

    assert document.extraction_status == "FAILED"
    assert "razao de compressao excede o limite" in (
        document.extraction_error
    )


def test_pdf_svrs_deve_continuar_usando_pypdf(monkeypatch, tmp_path):
    pdf_path = tmp_path / "documento.pdf"
    pdf_path.write_bytes(b"%PDF-1.7\nconteudo")
    chamadas = []
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(pdf_path)
    )

    def extrair_pdf(file_path):
        chamadas.append(file_path)
        return "Texto PDF"

    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "extract_pdf_text",
        extrair_pdf
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "normalize_text",
        lambda text: text
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao(download_url="https://example.com/documento.pdf")
    )

    assert document.extraction_status == "EXTRACTED"
    assert document.extractor_version == "svrs-pypdf-v1"
    assert chamadas == [str(pdf_path)]


def test_xlsx_svrs_nao_deve_ser_enviado_ao_pypdf(monkeypatch, tmp_path):
    xlsx_path = tmp_path / "tabela.xlsx"
    xlsx_path.write_bytes(b"PK\x03\x04xlsx")
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: str(xlsx_path)
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "extract_pdf_text",
        lambda file_path: (_ for _ in ()).throw(
            AssertionError("XLSX nao deve usar pypdf")
        )
    )

    document = document_extraction_service.extract_publication_document(
        criar_publicacao(
            download_url="https://example.com/tabela.xlsx"
        )
    )

    assert document.extraction_status == "PENDING"
    assert document.extractor_version is None
    assert document.extraction_error == "formato ainda nao suportado: xlsx"


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


def test_deve_gerar_publication_document_extraido_para_dou(monkeypatch):
    dou_url = (
        "https://www.in.gov.br/web/dou/-/"
        "ato-tecnico-conjunto-rfb-suara-cgibs"
    )
    html = criar_html_dou_com_conteudo_principal()

    class RespostaHttp:
        text = html

        def raise_for_status(self):
            pass

    monkeypatch.setattr(
        document_extraction_service.httpx,
        "get",
        lambda url, headers, timeout, follow_redirects: RespostaHttp(),
        raising=False
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: (_ for _ in ()).throw(
            AssertionError("Fluxo PDF nao deve ser usado para DOU")
        )
    )

    publication = criar_publicacao_dou(download_url=dou_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "EXTRACTED"
    assert document.source_url == dou_url
    assert document.content_text.startswith(
        "ATO TÉCNICO CONJUNTO RFB/SUARA/CGIBS"
    )
    assert "Documentação técnica aplicável à CBS e ao IBS" in (
        document.content_text
    )
    assert document.content_hash == hashlib.sha256(
        document.content_text.encode("utf-8")
    ).hexdigest()
    assert document.content_length == len(document.content_text)
    assert document.extraction_error is None
    assert document.extractor_version == "dou-html-v1"
    assert document.extracted_at is not None


def test_deve_enviar_headers_de_navegador_ao_baixar_html_do_dou(
    monkeypatch
):
    dou_url = (
        "https://www.in.gov.br/web/dou/-/"
        "ato-tecnico-conjunto-rfb-suara-cgibs"
    )
    html = criar_html_dou_com_conteudo_principal()
    requisicao = {}

    class RespostaHttp:
        text = html

        def raise_for_status(self):
            pass

    def baixar_html(url, headers, timeout, follow_redirects):
        requisicao["url"] = url
        requisicao["headers"] = headers
        requisicao["timeout"] = timeout
        requisicao["follow_redirects"] = follow_redirects
        return RespostaHttp()

    monkeypatch.setattr(
        document_extraction_service.httpx,
        "get",
        baixar_html,
        raising=False
    )

    publication = criar_publicacao_dou(download_url=dou_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "EXTRACTED"
    assert requisicao["url"] == dou_url
    assert requisicao["timeout"] == 30.0
    assert requisicao["follow_redirects"] is True
    assert "User-Agent" in requisicao["headers"]
    assert "Mozilla/5.0" in requisicao["headers"]["User-Agent"]
    assert "Accept-Language" in requisicao["headers"]
    assert requisicao["headers"]["Accept-Language"].startswith("pt-BR")


def test_deve_extrair_apenas_conteudo_principal_do_ato_dou(monkeypatch):
    dou_url = (
        "https://www.in.gov.br/web/dou/-/"
        "ato-tecnico-conjunto-rfb-suara-cgibs"
    )
    html = criar_html_dou_com_conteudo_principal()

    class RespostaHttp:
        text = html

        def raise_for_status(self):
            pass

    monkeypatch.setattr(
        document_extraction_service.httpx,
        "get",
        lambda url, headers, timeout, follow_redirects: RespostaHttp(),
        raising=False
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: (_ for _ in ()).throw(
            AssertionError("Fluxo PDF nao deve ser usado para DOU")
        )
    )

    publication = criar_publicacao_dou(download_url=dou_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert "ATO TÉCNICO CONJUNTO" in document.content_text
    assert "Manual de Habilitação de Participantes" in document.content_text
    assert "Caminho de Navegação" not in document.content_text
    assert "Compartilhe:" not in document.content_text
    assert "Brasão do Brasil" not in document.content_text
    assert "REPORTAR ERRO" not in document.content_text


def test_deve_retornar_pending_para_dou_sem_download_url():
    publication = criar_publicacao_dou(download_url=None)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "PENDING"
    assert document.source_url is None
    assert document.content_text is None
    assert document.content_hash is None
    assert document.content_length is None
    assert document.extraction_error is None


def test_deve_retornar_failed_para_dou_quando_download_falhar(monkeypatch):
    dou_url = (
        "https://www.in.gov.br/web/dou/-/"
        "ato-tecnico-conjunto-rfb-suara-cgibs"
    )

    def falhar_download(url, headers, timeout, follow_redirects):
        assert url == dou_url
        raise RuntimeError("dou indisponivel\nstack trace")

    monkeypatch.setattr(
        document_extraction_service.httpx,
        "get",
        falhar_download,
        raising=False
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: (_ for _ in ()).throw(
            RuntimeError("dou indisponivel\nstack trace")
        )
    )

    publication = criar_publicacao_dou(download_url=dou_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "FAILED"
    assert document.source_url == dou_url
    assert document.content_text is None
    assert document.content_hash is None
    assert document.content_length is None
    assert document.extraction_error == "dou indisponivel"
    assert "\n" not in document.extraction_error
    assert document.extractor_version == "dou-html-v1"
    assert document.extracted_at is not None


def test_deve_retornar_failed_para_dou_quando_conteudo_principal_nao_for_encontrado(
    monkeypatch
):
    dou_url = (
        "https://www.in.gov.br/web/dou/-/"
        "ato-tecnico-conjunto-rfb-suara-cgibs"
    )

    class RespostaHttp:
        text = """
        <html>
            <main>
                <nav>Caminho de Navegação</nav>
                <div>Menu e rodapé sem conteúdo do ato.</div>
            </main>
        </html>
        """

        def raise_for_status(self):
            pass

    monkeypatch.setattr(
        document_extraction_service.httpx,
        "get",
        lambda url, headers, timeout, follow_redirects: RespostaHttp(),
        raising=False
    )
    monkeypatch.setattr(
        document_extraction_service.svrs_collector,
        "download_document",
        lambda url, filename: (_ for _ in ()).throw(
            AssertionError("Fluxo PDF nao deve ser usado para DOU")
        )
    )

    publication = criar_publicacao_dou(download_url=dou_url)

    document = document_extraction_service.extract_publication_document(
        publication
    )

    assert document.extraction_status == "FAILED"
    assert document.source_url == dou_url
    assert document.content_text is None
    assert document.content_hash is None
    assert document.content_length is None
    assert document.extractor_version == "dou-html-v1"
    assert document.extraction_error == (
        "conteudo principal do DOU nao encontrado"
    )
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


def criar_publicacao_schema(download_url: str | None) -> Publication:
    return Publication(
        external_id="f" * 64,
        source="SVRS",
        title="Pacote de schemas - NT 2022.002 v1.30",
        document_type="SCHEMA",
        published_at=datetime.now(),
        download_url=download_url
    )


def criar_zip_schema(
    tmp_path: Path,
    files: dict[str, str],
    filename: str = "schema.zip",
    compression: int = zipfile.ZIP_DEFLATED
) -> Path:
    zip_path = tmp_path / filename

    with zipfile.ZipFile(zip_path, "w", compression=compression) as archive:
        for path, content in files.items():
            archive.writestr(path, content)

    return zip_path


def criar_publicacao_dou(download_url: str | None) -> Publication:
    return Publication(
        external_id="e" * 64,
        source="IMPRENSA_NACIONAL_DOU",
        title=(
            "ATO TÉCNICO CONJUNTO RFB/SUARA/CGIBS/DIRETORIA-EXECUTIVA "
            "Nº 4, DE 28 DE AGOSTO DE 2026"
        ),
        document_type="ATO_TECNICO",
        published_at=datetime.now(),
        download_url=download_url
    )


def criar_html_dou_com_conteudo_principal() -> str:
    return """
    <html>
        <body>
            <main id="content">
                <nav>Caminho de Navegação</nav>
                <h2>Publicador de Conteúdos e Mídias</h2>
                <article id="materia">
                    <div class="row-fluid">
                        <div class="cabecalho-dou text-center">
                            Brasão do Brasil Diário Oficial da União
                        </div>
                        <div class="detalhes-dou">
                            Publicado em: 08/09/2026 | Edição: 169
                        </div>
                        <div class="dou-modelo">
                            <a>Voltar</a>
                            <span>Compartilhe:</span>
                            <div class="texto-dou">
                                <html>
                                    <body>
                                        <p>ATO TÉCNICO CONJUNTO RFB/SUARA/CGIBS/DIRETORIA-EXECUTIVA Nº 4, DE 28 DE AGOSTO DE 2026</p>
                                        <p>Documentação técnica aplicável à CBS e ao IBS.</p>
                                        <p>Art. 1º Fica aprovada a documentação técnica a seguir indicada.</p>
                                        <p>I - Manual de Habilitação de Participantes - versão 1.0.0.</p>
                                    </body>
                                </html>
                            </div>
                        </div>
                    </div>
                </article>
                <section>REPORTAR ERRO</section>
            </main>
        </body>
    </html>
    """


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
