import hashlib

from app.service.svrs_schema_artifact_parser_service import (
    parse_svrs_schema_artifacts
)


def test_deve_gerar_schema_artifact_para_um_xsd():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                (
                    "schemas/nfe.xsd",
                    "<xs:schema><xs:element name=\"NFe\"/></xs:schema>"
                )
            ]
        )
    )

    assert len(artifacts) == 1
    assert artifacts[0].path == "schemas/nfe.xsd"
    assert artifacts[0].content == (
        "<xs:schema><xs:element name=\"NFe\"/></xs:schema>"
    )


def test_deve_gerar_varios_schema_artifacts():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                ("schemas/a.xsd", "<xs:schema>A</xs:schema>"),
                ("schemas/b.xsd", "<xs:schema>B</xs:schema>")
            ]
        )
    )

    assert len(artifacts) == 2
    assert [artifact.path for artifact in artifacts] == [
        "schemas/a.xsd",
        "schemas/b.xsd"
    ]


def test_deve_preservar_caminho_informado_no_marcador():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                ("schemas/sub/pedido-v1.00.xsd", "<xs:schema/>")
            ]
        )
    )

    assert artifacts[0].path == "schemas/sub/pedido-v1.00.xsd"


def test_content_deve_conter_somente_xml_sem_marcador():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                (
                    "schemas/nfe.xsd",
                    (
                        "<?xml version=\"1.0\"?>\n"
                        "<xs:schema>\n"
                        "    <xs:element name=\"NFe\"/>\n"
                        "</xs:schema>"
                    )
                )
            ]
        )
    )

    assert "=== arquivo:" not in artifacts[0].content
    assert artifacts[0].content.startswith("<?xml")
    assert artifacts[0].content.endswith("</xs:schema>")


def test_deve_calcular_content_hash_sha256_individual():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                ("schemas/a.xsd", "<xs:schema>A</xs:schema>"),
                ("schemas/b.xsd", "<xs:schema>B</xs:schema>")
            ]
        )
    )

    assert artifacts[0].content_hash == sha256(
        "<xs:schema>A</xs:schema>"
    )
    assert artifacts[1].content_hash == sha256(
        "<xs:schema>B</xs:schema>"
    )


def test_deve_preservar_ordem_deterministica_do_content_text():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                ("schemas/z.xsd", "<xs:schema>Z</xs:schema>"),
                ("schemas/a.xsd", "<xs:schema>A</xs:schema>"),
                ("schemas/m.xsd", "<xs:schema>M</xs:schema>")
            ]
        )
    )

    assert [artifact.path for artifact in artifacts] == [
        "schemas/z.xsd",
        "schemas/a.xsd",
        "schemas/m.xsd"
    ]


def test_deve_tratar_marcador_sem_conteudo_de_forma_segura():
    artifacts = parse_svrs_schema_artifacts(
        "=== arquivo: schemas/vazio.xsd ===\n\n"
    )

    assert artifacts == []


def test_content_text_vazio_deve_retornar_lista_vazia():
    assert parse_svrs_schema_artifacts("") == []
    assert parse_svrs_schema_artifacts(None) == []


def test_texto_sem_marcador_valido_nao_deve_inventar_schema_artifact():
    artifacts = parse_svrs_schema_artifacts(
        "<xs:schema><xs:element name=\"NFe\"/></xs:schema>"
    )

    assert artifacts == []


def test_nao_deve_interpretar_texto_xml_com_palavra_arquivo_como_marcador():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                (
                    "schemas/nfe.xsd",
                    (
                        "<xs:schema>\n"
                        "    <xs:documentation>\n"
                        "        === arquivo: nao-e-marcador.xsd ===\n"
                        "    </xs:documentation>\n"
                        "</xs:schema>"
                    )
                )
            ]
        )
    )

    assert len(artifacts) == 1
    assert artifacts[0].path == "schemas/nfe.xsd"
    assert "nao-e-marcador.xsd" in artifacts[0].content


def test_deve_remover_raiz_versionada_v120_do_path_logico():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                (
                    "PL_010b_NT2025_002_v1.20/leiauteNFe_v4.00.xsd",
                    "<xs:schema/>"
                )
            ]
        )
    )

    assert artifacts[0].path == "leiauteNFe_v4.00.xsd"


def test_deve_remover_raiz_versionada_v130_do_path_logico():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                (
                    "PL_010b_NT2025_002_v1.30/leiauteNFe_v4.00.xsd",
                    "<xs:schema/>"
                )
            ]
        )
    )

    assert artifacts[0].path == "leiauteNFe_v4.00.xsd"


def test_deve_preservar_subdiretorios_internos_apos_raiz_do_pacote():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                (
                    (
                        "PL_010b_NT2025_002_v1.30/"
                        "schemas/eventos/evento.xsd"
                    ),
                    "<xs:schema/>"
                )
            ]
        )
    )

    assert artifacts[0].path == "schemas/eventos/evento.xsd"


def test_deve_gerar_mesmo_path_logico_para_arquivos_de_versoes_diferentes():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                (
                    "PL_010b_NT2025_002_v1.20/leiauteNFe_v4.00.xsd",
                    "<xs:schema>v120</xs:schema>"
                ),
                (
                    "PL_010b_NT2025_002_v1.30/leiauteNFe_v4.00.xsd",
                    "<xs:schema>v130</xs:schema>"
                )
            ]
        )
    )

    assert [artifact.path for artifact in artifacts] == [
        "leiauteNFe_v4.00.xsd",
        "leiauteNFe_v4.00.xsd"
    ]


def test_nao_deve_alterar_path_que_ja_nao_tem_diretorio_raiz():
    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                ("leiauteNFe_v4.00.xsd", "<xs:schema/>")
            ]
        )
    )

    assert artifacts[0].path == "leiauteNFe_v4.00.xsd"


def test_normalizacao_de_path_nao_deve_alterar_conteudo():
    content = (
        "<?xml version=\"1.0\"?>\n"
        "<xs:schema>\n"
        "    <xs:element name=\"NFe\"/>\n"
        "</xs:schema>"
    )

    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                ("PL_010b_NT2025_002_v1.30/nfe_v4.00.xsd", content)
            ]
        )
    )

    assert artifacts[0].content == content


def test_content_hash_deve_continuar_baseado_somente_no_conteudo():
    content = "<xs:schema><xs:element name=\"NFe\"/></xs:schema>"

    artifacts = parse_svrs_schema_artifacts(
        criar_content_text(
            [
                ("PL_010b_NT2025_002_v1.20/nfe_v4.00.xsd", content),
                ("PL_010b_NT2025_002_v1.30/nfe_v4.00.xsd", content)
            ]
        )
    )

    assert artifacts[0].content_hash == sha256(content)
    assert artifacts[1].content_hash == sha256(content)
    assert artifacts[0].content_hash == artifacts[1].content_hash


def criar_content_text(files: list[tuple[str, str]]) -> str:
    return "\n\n".join(
        f"=== arquivo: {path} ===\n{content}"
        for path, content in files
    )


def sha256(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()
