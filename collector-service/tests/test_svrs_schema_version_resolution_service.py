from app.model.svrs_schema_identity import SvrsSchemaPackageIdentity
from app.service.svrs_schema_version_resolution_service import (
    resolve_previous_svrs_schema_version
)


def test_deve_encontrar_versao_anterior_imediata_para_130():
    current = criar_identidade_resolvida("1.30", [1, 30])
    known = [
        criar_identidade_resolvida("1.10", [1, 10]),
        criar_identidade_resolvida("1.20", [1, 20]),
        current
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "RESOLVED"
    assert result.previous_identity.raw_version == "1.20"


def test_deve_encontrar_versao_anterior_imediata_para_120():
    current = criar_identidade_resolvida("1.20", [1, 20])
    known = [
        criar_identidade_resolvida("1.10", [1, 10]),
        current,
        criar_identidade_resolvida("1.30", [1, 30])
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "RESOLVED"
    assert result.previous_identity.raw_version == "1.10"


def test_deve_retornar_none_quando_atual_for_primeira_versao():
    current = criar_identidade_resolvida("1.10", [1, 10])
    known = [
        current,
        criar_identidade_resolvida("1.20", [1, 20])
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "NOT_FOUND"
    assert result.previous_identity is None


def test_nao_deve_misturar_family_id_diferentes():
    current = criar_identidade_resolvida("1.30", [1, 30])
    known = [
        criar_identidade_resolvida(
            "1.20",
            [1, 20],
            family_id="SCHEMA_PACKAGE:NT:2025.001:PL_009q"
        )
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "NOT_FOUND"
    assert result.previous_identity is None


def test_deve_ignorar_identidades_ambiguas():
    current = criar_identidade_resolvida("1.30", [1, 30])
    known = [
        criar_identidade_resolvida("1.10", [1, 10]),
        criar_identidade_ambigua("1.20", [1, 20])
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "RESOLVED"
    assert result.previous_identity.raw_version == "1.10"


def test_deve_ignorar_identidades_nao_resolvidas():
    current = criar_identidade_resolvida("1.30", [1, 30])
    known = [
        criar_identidade_resolvida("1.10", [1, 10]),
        criar_identidade_nao_resolvida("1.20", [1, 20])
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "RESOLVED"
    assert result.previous_identity.raw_version == "1.10"


def test_deve_ordenar_versoes_numericamente():
    current = criar_identidade_resolvida("1.10", [1, 10])
    known = [
        criar_identidade_resolvida("1.9", [1, 9]),
        current
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "RESOLVED"
    assert result.previous_identity.raw_version == "1.9"


def test_deve_ordenar_sufixos_de_forma_deterministica():
    current = criar_identidade_resolvida("1.20b", [1, 20], "b")
    known = [
        criar_identidade_resolvida("1.20", [1, 20]),
        criar_identidade_resolvida("1.20a", [1, 20], "a"),
        current
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "RESOLVED"
    assert result.previous_identity.raw_version == "1.20a"


def test_nao_deve_considerar_a_propria_versao_como_anterior():
    current = criar_identidade_resolvida("1.20", [1, 20])
    known = [
        current
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "NOT_FOUND"
    assert result.previous_identity is None


def test_deve_marcar_ambiguo_quando_houver_duplicidade_exata_da_anterior():
    current = criar_identidade_resolvida("1.30", [1, 30])
    known = [
        criar_identidade_resolvida("1.20", [1, 20]),
        criar_identidade_resolvida("1.20", [1, 20]),
        current
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "AMBIGUOUS"
    assert result.previous_identity is None
    assert len(result.candidate_identities) == 2


def test_nao_deve_escolher_versao_maior_que_a_atual():
    current = criar_identidade_resolvida("1.20", [1, 20])
    known = [
        current,
        criar_identidade_resolvida("1.30", [1, 30])
    ]

    result = resolve_previous_svrs_schema_version(current, known)

    assert result.status == "NOT_FOUND"
    assert result.previous_identity is None


def criar_identidade_resolvida(
    raw_version: str,
    version_parts: list[int],
    version_suffix: str | None = None,
    family_id: str = "SCHEMA_PACKAGE:NT:2025.002:PL_010b"
) -> SvrsSchemaPackageIdentity:
    return SvrsSchemaPackageIdentity(
        nt="2025.002",
        version_parts=version_parts,
        version_suffix=version_suffix,
        raw_version=raw_version,
        package_code=family_id.rsplit(":", 1)[-1],
        family_id=family_id,
        status="RESOLVED"
    )


def criar_identidade_ambigua(
    raw_version: str,
    version_parts: list[int]
) -> SvrsSchemaPackageIdentity:
    identity = criar_identidade_resolvida(raw_version, version_parts)
    identity.status = "AMBIGUOUS"

    return identity


def criar_identidade_nao_resolvida(
    raw_version: str,
    version_parts: list[int]
) -> SvrsSchemaPackageIdentity:
    identity = criar_identidade_resolvida(raw_version, version_parts)
    identity.status = "UNRESOLVED"

    return identity
