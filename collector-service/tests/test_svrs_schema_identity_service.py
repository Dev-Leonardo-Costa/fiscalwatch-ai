from app.service.svrs_schema_identity_service import (
    parse_svrs_schema_package_identity
)


def test_deve_resolver_identidade_de_pacote_schema_nt_com_versao():
    result = parse_svrs_schema_package_identity(
        title="Pacote de schemas - NT 2025.002 v1.30",
        download_url=criar_url("PL_010b_NT2025_002_v1.30.zip")
    )

    assert result.nt == "2025.002"
    assert result.version_parts == [1, 30]
    assert result.version_suffix is None
    assert result.raw_version == "v1.30"
    assert result.package_code == "PL_010b"
    assert result.family_id == "SCHEMA_PACKAGE:NT:2025.002:PL_010b"
    assert result.status == "RESOLVED"


def test_deve_entender_versao_com_ponto_apos_v():
    result = parse_svrs_schema_package_identity(
        title="Pacote de schemas - NT 2025.002 v.1.20",
        download_url=criar_url("PL_010b_NT2025_002_v1.20.zip")
    )

    assert result.nt == "2025.002"
    assert result.version_parts == [1, 20]
    assert result.raw_version == "v.1.20"
    assert result.package_code == "PL_010b"
    assert result.status == "RESOLVED"


def test_deve_preservar_sufixo_de_versao():
    result = parse_svrs_schema_package_identity(
        title=(
            "Schemas XML NF-e/NFC-e - Pacote de Liberação No. 9 "
            "(Novo leiaute da NF-e, NT 2019.001 v.1.20a)"
        ),
        download_url=criar_url("PL_009_V4_00_NT_2019_001_v1.20a.zip")
    )

    assert result.nt == "2019.001"
    assert result.version_parts == [1, 20]
    assert result.version_suffix == "a"
    assert result.raw_version == "v.1.20a"
    assert result.package_code == "PL_009"
    assert result.family_id == "SCHEMA_PACKAGE:NT:2019.001:PL_009"
    assert result.status == "RESOLVED"


def test_deve_interpretar_versao_compacta_do_filename_quando_confirmar_contexto():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2023.001 v1.20",
        download_url=criar_url("PL_009k_NT2023_001_v120.zip")
    )

    assert result.nt == "2023.001"
    assert result.version_parts == [1, 20]
    assert result.version_suffix is None
    assert result.raw_version == "v1.20"
    assert result.package_code == "PL_009k"
    assert result.status == "RESOLVED"


def test_deve_interpretar_versao_compacta_com_sufixo_do_filename():
    result = parse_svrs_schema_package_identity(
        title="Pacote de schemas - NT 2022.003 v1.00c",
        download_url=criar_url("PL_009j_NT2022_003_v100c.zip")
    )

    assert result.nt == "2022.003"
    assert result.version_parts == [1, 0]
    assert result.version_suffix == "c"
    assert result.raw_version == "v1.00c"
    assert result.package_code == "PL_009j"
    assert result.status == "RESOLVED"


def test_nao_deve_inventar_versao_quando_titulo_nao_tem_versao():
    result = parse_svrs_schema_package_identity(
        title="Pacote de schemas - NT 2025.001",
        download_url=criar_url("PL_009q_NT2025_001_v1.00.zip")
    )

    assert result.nt == "2025.001"
    assert result.version_parts is None
    assert result.raw_version is None
    assert result.package_code == "PL_009q"
    assert result.family_id is None
    assert result.status == "UNRESOLVED"


def test_nao_deve_inventar_nt_quando_titulo_nao_tem_nt():
    result = parse_svrs_schema_package_identity(
        title="Pacote de Schemas Econf",
        download_url=criar_url("Evento_Econf_v1.00.zip")
    )

    assert result.nt is None
    assert result.version_parts is None
    assert result.family_id is None
    assert result.status == "UNRESOLVED"


def test_deve_marcar_ambiguo_quando_titulo_e_filename_divergem_na_versao():
    result = parse_svrs_schema_package_identity(
        title="Pacote de schemas - NT 2025.002 v.1.10",
        download_url=criar_url("PL_010b_NT2025_002_v1.00.zip")
    )

    assert result.nt == "2025.002"
    assert result.version_parts is None
    assert result.raw_version is None
    assert result.package_code == "PL_010b"
    assert result.family_id is None
    assert result.status == "AMBIGUOUS"


def test_deve_marcar_ambiguo_quando_titulo_tem_duas_nts():
    result = parse_svrs_schema_package_identity(
        title="Schemas da NT 2023.004 v1.11 e NT 2019.001 v1.62",
        download_url=criar_url("PL_009n_NT2023_004_v101_e_NT2019_001_v162.zip")
    )

    assert result.nt is None
    assert result.version_parts is None
    assert result.package_code == "PL_009n"
    assert result.family_id is None
    assert result.status == "AMBIGUOUS"


def test_nao_deve_confundir_v4_00_do_leiaute_com_versao_do_pacote():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2020.006 - MarketPlace v1.00",
        download_url=criar_url("PL_009_V4_00_NT_2020_006_v1.00.zip")
    )

    assert result.nt == "2020.006"
    assert result.version_parts == [1, 0]
    assert result.raw_version == "v1.00"
    assert result.package_code == "PL_009"
    assert result.family_id == "SCHEMA_PACKAGE:NT:2020.006:PL_009"
    assert result.status == "RESOLVED"


def test_deve_resolver_package_code_para_eventos_rtc():
    result = parse_svrs_schema_package_identity(
        title="Pacote de schemas - Eventos da NT 2025.002 v1.30 - RTC",
        download_url=criar_url("Eventos_RTC.zip")
    )

    assert result.nt == "2025.002"
    assert result.version_parts == [1, 30]
    assert result.package_code == "EVENTOS_RTC"
    assert result.family_id == "SCHEMA_PACKAGE:NT:2025.002:EVENTOS_RTC"
    assert result.status == "RESOLVED"


def test_deve_resolver_package_code_legivel_para_evento_insucesso_nfe():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2023.005 v1.02",
        download_url=criar_url("Evento_InsucessoNFe_PL_v1.02.zip")
    )

    assert result.nt == "2023.005"
    assert result.version_parts == [1, 2]
    assert result.package_code == "EVENTO_INSUCESSO_NFE"
    assert result.family_id == (
        "SCHEMA_PACKAGE:NT:2023.005:EVENTO_INSUCESSO_NFE"
    )
    assert result.status == "RESOLVED"


def test_deve_resolver_package_code_legivel_para_evento_ator_interessado():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2020.007 - Evento do Transportador Interessado v1.01",
        download_url=criar_url("Evento_AtorInteressado_PL_v1.01.zip")
    )

    assert result.nt == "2020.007"
    assert result.version_parts == [1, 1]
    assert result.package_code == "EVENTO_ATOR_INTERESSADO"
    assert result.family_id == (
        "SCHEMA_PACKAGE:NT:2020.007:EVENTO_ATOR_INTERESSADO"
    )
    assert result.status == "RESOLVED"


def test_deve_remover_versao_final_do_package_code_de_evento_econf():
    result = parse_svrs_schema_package_identity(
        title="Pacote de Schemas Econf NT 2024.001 v1.00",
        download_url=criar_url("Evento_Econf_v1.00.zip")
    )

    assert result.package_code == "EVENTO_ECONF"
    assert result.status == "RESOLVED"
    assert "_V1" not in result.package_code


def test_deve_remover_pl_final_de_evento_cce():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2014.001 v1.01",
        download_url=criar_url("Evento_CCe_PL_v1.01.zip")
    )

    assert result.package_code == "EVENTO_CCE"
    assert result.status == "RESOLVED"


def test_deve_remover_pl_final_de_evento_epec():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2014.002 v1.01",
        download_url=criar_url("Evento_EPEC_PL_v1.01.zip")
    )

    assert result.package_code == "EVENTO_EPEC"
    assert result.status == "RESOLVED"


def test_deve_remover_pl_final_de_evento_generico():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2014.003 v1.01",
        download_url=criar_url("Evento_Generico_PL_v1.01.zip")
    )

    assert result.package_code == "EVENTO_GENERICO"
    assert result.status == "RESOLVED"


def test_deve_remover_pl_final_de_evento_manifesta_destinatario():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2014.004 v1.01",
        download_url=criar_url("Evento_ManifestaDest_PL_v1.01.zip")
    )

    assert result.package_code == "EVENTO_MANIFESTA_DEST"
    assert result.status == "RESOLVED"


def test_package_code_nao_deve_conter_extensao_versao_final_ou_pl_final():
    filenames = [
        ("Evento_Econf_v1.00.zip", "v1.00"),
        ("Evento_CCe_PL_v1.01.zip", "v1.01"),
        ("Evento_EPEC_PL_v1.01.zip", "v1.01"),
        ("Evento_Generico_PL_v1.01.zip", "v1.01"),
        ("Evento_ManifestaDest_PL_v1.01.zip", "v1.01")
    ]

    for index, (filename, version) in enumerate(filenames, start=1):
        result = parse_svrs_schema_package_identity(
            title=f"Schemas NT 2014.{index:03d} {version}",
            download_url=criar_url(filename)
        )

        assert result.status == "RESOLVED"
        assert ".ZIP" not in result.package_code
        assert "_V1" not in result.package_code
        assert not result.package_code.endswith("_PL")


def test_nao_deve_resolver_sem_package_code_seguro():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2022.001 v1.00",
        download_url=criar_url("schemas.zip")
    )

    assert result.nt == "2022.001"
    assert result.version_parts == [1, 0]
    assert result.package_code is None
    assert result.family_id is None
    assert result.status == "UNRESOLVED"


def test_deve_usar_versao_compacta_120_do_filename_quando_titulo_nao_tem_versao():
    result = parse_svrs_schema_package_identity(
        title=(
            "Schemas PL 9k (NT 2023.001) - "
            "Tributação Monofásica de Combustíveis"
        ),
        download_url=criar_url("PL_009k_NT2023_001_v120.zip")
    )

    assert result.nt == "2023.001"
    assert result.version_parts == [1, 20]
    assert result.version_suffix is None
    assert result.raw_version == "v120"
    assert result.package_code == "PL_009k"
    assert result.family_id == "SCHEMA_PACKAGE:NT:2023.001:PL_009k"
    assert result.status == "RESOLVED"


def test_deve_usar_nt_e_versao_compacta_100a_do_filename():
    result = parse_svrs_schema_package_identity(
        title="Pacote de Schemas PL 009j",
        download_url=criar_url("PL_009j_NT2022_003_v100a.zip")
    )

    assert result.nt == "2022.003"
    assert result.version_parts == [1, 0]
    assert result.version_suffix == "a"
    assert result.raw_version == "v100a"
    assert result.package_code == "PL_009j"
    assert result.family_id == "SCHEMA_PACKAGE:NT:2022.003:PL_009j"
    assert result.status == "RESOLVED"


def test_deve_usar_versao_compacta_100c_do_filename():
    result = parse_svrs_schema_package_identity(
        title="Schemas NF-e 009i da NT 2021.004",
        download_url=criar_url("PL_009i_NT2021_004_v100c.zip")
    )

    assert result.nt == "2021.004"
    assert result.version_parts == [1, 0]
    assert result.version_suffix == "c"
    assert result.raw_version == "v100c"
    assert result.package_code == "PL_009i"
    assert result.family_id == "SCHEMA_PACKAGE:NT:2021.004:PL_009i"
    assert result.status == "RESOLVED"


def test_deve_ignorar_v4_00_e_usar_v120_como_versao_do_pacote():
    result = parse_svrs_schema_package_identity(
        title="Schemas NF-e 009g da NT 2020.005",
        download_url=criar_url("PL_009g_V4_00_NT_2020_005_v120.zip")
    )

    assert result.nt == "2020.005"
    assert result.version_parts == [1, 20]
    assert result.version_suffix is None
    assert result.raw_version == "v120"
    assert result.package_code == "PL_009g"
    assert result.family_id == "SCHEMA_PACKAGE:NT:2020.005:PL_009g"
    assert result.status == "RESOLVED"


def test_deve_manter_ambiguo_quando_v100_diverge_de_v110_do_titulo():
    result = parse_svrs_schema_package_identity(
        title="Schemas NT 2024.001 v1.10",
        download_url=criar_url("PL_009o_NT2024_001_v100.zip")
    )

    assert result.nt == "2024.001"
    assert result.version_parts is None
    assert result.raw_version is None
    assert result.package_code == "PL_009o"
    assert result.family_id is None
    assert result.status == "AMBIGUOUS"


def test_deve_manter_ambiguo_para_titulo_com_multiplas_nts_e_versoes_compactas():
    result = parse_svrs_schema_package_identity(
        title="Schemas da NT 2023.004 v1.11 e NT 2019.001 v1.62",
        download_url=criar_url("PL_009n_NT2023_004_v101_e_NT2019_001_v162.zip")
    )

    assert result.nt is None
    assert result.version_parts is None
    assert result.raw_version is None
    assert result.package_code == "PL_009n"
    assert result.family_id is None
    assert result.status == "AMBIGUOUS"


def test_deve_manter_ambiguo_para_divergencia_explicita_v110_v100():
    result = parse_svrs_schema_package_identity(
        title="Pacote de schemas - NT 2025.002 v.1.10",
        download_url=criar_url("PL_010b_NT2025_002_v1.00.zip")
    )

    assert result.nt == "2025.002"
    assert result.version_parts is None
    assert result.raw_version is None
    assert result.package_code == "PL_010b"
    assert result.family_id is None
    assert result.status == "AMBIGUOUS"


def test_nao_deve_interpretar_versao_compacta_com_dois_ou_quatro_digitos():
    casos = [
        "PL_999_NT2024_001_v12.zip",
        "PL_999_NT2024_001_v1234.zip"
    ]

    for filename in casos:
        result = parse_svrs_schema_package_identity(
            title="Schemas NT 2024.001",
            download_url=criar_url(filename)
        )

        assert result.nt == "2024.001"
        assert result.version_parts is None
        assert result.raw_version is None
        assert result.package_code == "PL_999"
        assert result.family_id is None
        assert result.status == "UNRESOLVED"


def criar_url(filename: str) -> str:
    return (
        "https://dfe-portal.svrs.rs.gov.br/NFE/"
        "DownloadArquivoEstatico/?sistema=NFE&tipoArquivo=2"
        f"&nomeArquivo={filename}"
    )
