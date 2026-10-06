from datetime import datetime

from app.model.fiscal_publication_history import (
    FiscalPublicationHistoryDocument,
    FiscalPublicationHistoryItem
)
from app.model.publication import Publication
from app.model.publication_document import PublicationDocument
from app.model.svrs_schema_identity import (
    SvrsSchemaPackageIdentity,
    SvrsSchemaPreviousVersionResolution
)
from app.service.svrs_schema_comparison_orchestration_service import (
    compare_current_svrs_schema_publication
)


def test_deve_encontrar_versao_anterior_e_comparar(monkeypatch):
    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "fetch_svrs_schema_publication_history"
        ),
        lambda base_url=None: [
            criar_historico("anterior", "v1.20", [1, 20], schema_sem_obs()),
            criar_historico("atual", "v1.30", [1, 30], schema_com_obs())
        ]
    )

    result = compare_current_svrs_schema_publication(
        current_publication=criar_publicacao_atual(),
        current_document=criar_documento(schema_com_obs())
    )

    assert result.status == "COMPARED"
    assert result.reason is None
    assert result.current_external_id == "atual"
    assert result.previous_external_id == "anterior"
    assert result.current_identity.raw_version == "v1.30"
    assert result.previous_identity.raw_version == "v1.20"
    assert result.total_changes == 1
    assert result.changes[0].change_type == "ELEMENT_ADDED"
    assert result.changes[0].symbol_name == "obs"


def test_deve_retornar_sem_comparacao_quando_identidade_atual_nao_resolvida(
    monkeypatch
):
    chamado = False

    def fetch_history(base_url=None):
        nonlocal chamado
        chamado = True
        return []

    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "fetch_svrs_schema_publication_history"
        ),
        fetch_history
    )

    result = compare_current_svrs_schema_publication(
        current_publication=criar_publicacao_atual(
            title="Pacote de schemas - NT 2025.002",
            download_url=url("PL_010b_NT2025_002_v1.30.zip")
        ),
        current_document=criar_documento(schema_com_obs())
    )

    assert result.status == "SKIPPED"
    assert result.reason == "CURRENT_IDENTITY_NOT_RESOLVED"
    assert result.current_identity.status == "UNRESOLVED"
    assert result.total_changes == 0
    assert chamado is False


def test_deve_tratar_historico_vazio(monkeypatch):
    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "fetch_svrs_schema_publication_history"
        ),
        lambda base_url=None: []
    )

    result = compare_current_svrs_schema_publication(
        current_publication=criar_publicacao_atual(),
        current_document=criar_documento(schema_com_obs())
    )

    assert result.status == "SKIPPED"
    assert result.reason == "HISTORY_EMPTY"
    assert result.previous_identity is None
    assert result.total_changes == 0


def test_deve_tratar_ausencia_de_versao_anterior(monkeypatch):
    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "fetch_svrs_schema_publication_history"
        ),
        lambda base_url=None: [
            criar_historico("atual", "v1.30", [1, 30], schema_com_obs())
        ]
    )

    result = compare_current_svrs_schema_publication(
        current_publication=criar_publicacao_atual(),
        current_document=criar_documento(schema_com_obs())
    )

    assert result.status == "SKIPPED"
    assert result.reason == "PREVIOUS_VERSION_NOT_FOUND"
    assert result.previous_identity is None
    assert result.total_changes == 0


def test_deve_ignorar_historico_de_outra_familia(monkeypatch):
    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "fetch_svrs_schema_publication_history"
        ),
        lambda base_url=None: [
            criar_historico(
                "outra-familia",
                "v1.20",
                [1, 20],
                schema_sem_obs(),
                nt="2025.001",
                package_code="PL_009q"
            )
        ]
    )

    result = compare_current_svrs_schema_publication(
        current_publication=criar_publicacao_atual(),
        current_document=criar_documento(schema_com_obs())
    )

    assert result.status == "SKIPPED"
    assert result.reason == "PREVIOUS_VERSION_NOT_FOUND"
    assert result.total_changes == 0


def test_deve_ignorar_identidade_historica_ambigua_ou_unresolved(
    monkeypatch
):
    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "fetch_svrs_schema_publication_history"
        ),
        lambda base_url=None: [
            criar_historico_com_titulo(
                "ambigua",
                (
                    "Schemas da NT 2023.004 v1.11 e "
                    "NT 2019.001 v1.62"
                ),
                "PL_009n_NT2023_004_v101_e_NT2019_001_v162.zip"
            ),
            criar_historico_com_titulo(
                "unresolved",
                "Pacote de schemas - NT 2025.002",
                "PL_010b_NT2025_002_v1.20.zip"
            )
        ]
    )

    result = compare_current_svrs_schema_publication(
        current_publication=criar_publicacao_atual(),
        current_document=criar_documento(schema_com_obs())
    )

    assert result.status == "SKIPPED"
    assert result.reason == "PREVIOUS_VERSION_NOT_FOUND"
    assert result.total_changes == 0


def test_deve_tratar_documento_anterior_nao_localizado(monkeypatch):
    previous_identity = SvrsSchemaPackageIdentity(
        nt="2025.002",
        version_parts=[1, 20],
        raw_version="v1.20",
        package_code="PL_010b",
        family_id="SCHEMA_PACKAGE:NT:2025.002:PL_010b",
        status="RESOLVED"
    )

    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "fetch_svrs_schema_publication_history"
        ),
        lambda base_url=None: [
            criar_historico("atual", "v1.30", [1, 30], schema_com_obs())
        ]
    )
    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "resolve_previous_svrs_schema_version"
        ),
        lambda current_identity, known_identities: (
            SvrsSchemaPreviousVersionResolution(
                status="RESOLVED",
                previous_identity=previous_identity,
                candidate_identities=[previous_identity]
            )
        )
    )

    result = compare_current_svrs_schema_publication(
        current_publication=criar_publicacao_atual(),
        current_document=criar_documento(schema_com_obs())
    )

    assert result.status == "SKIPPED"
    assert result.reason == "PREVIOUS_DOCUMENT_NOT_FOUND"
    assert result.previous_identity == previous_identity
    assert result.total_changes == 0


def test_deve_comparar_sem_mudancas(monkeypatch):
    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "fetch_svrs_schema_publication_history"
        ),
        lambda base_url=None: [
            criar_historico("anterior", "v1.20", [1, 20], schema_com_obs())
        ]
    )

    result = compare_current_svrs_schema_publication(
        current_publication=criar_publicacao_atual(),
        current_document=criar_documento(schema_com_obs())
    )

    assert result.status == "COMPARED"
    assert result.previous_external_id == "anterior"
    assert result.changes == []
    assert result.total_changes == 0


def test_deve_comparar_com_mudancas(monkeypatch):
    monkeypatch.setattr(
        (
            "app.service.svrs_schema_comparison_orchestration_service."
            "fetch_svrs_schema_publication_history"
        ),
        lambda base_url=None: [
            criar_historico("anterior", "v1.20", [1, 20], schema_sem_obs())
        ]
    )

    result = compare_current_svrs_schema_publication(
        current_publication=criar_publicacao_atual(),
        current_document=criar_documento(schema_com_obs())
    )

    assert result.status == "COMPARED"
    assert result.total_changes == 1
    assert [change.change_type for change in result.changes] == [
        "ELEMENT_ADDED"
    ]


def criar_publicacao_atual(
    title: str = "Pacote de schemas - NT 2025.002 v1.30",
    download_url: str | None = None
) -> Publication:
    return Publication(
        external_id="atual",
        source="SVRS",
        title=title,
        document_type="SCHEMA",
        published_at=datetime(2026, 1, 2, 0, 0),
        download_url=download_url or url("PL_010b_NT2025_002_v1.30.zip")
    )


def criar_historico(
    external_id: str,
    raw_version: str,
    version_parts: list[int],
    content: str,
    nt: str = "2025.002",
    package_code: str = "PL_010b"
) -> FiscalPublicationHistoryItem:
    version_suffix = ""

    if raw_version[-1:].isalpha():
        version_suffix = raw_version[-1]

    title_version = raw_version

    if len(version_parts) == 2 and not version_suffix:
        title_version = f"v{version_parts[0]}.{version_parts[1]:02d}"

    return criar_historico_com_titulo(
        external_id=external_id,
        title=f"Pacote de schemas - NT {nt} {title_version}",
        filename=(
            f"{package_code}_NT{nt.replace('.', '_')}_"
            f"{raw_version}.zip"
        ),
        content=content
    )


def criar_historico_com_titulo(
    external_id: str,
    title: str,
    filename: str,
    content: str = None
) -> FiscalPublicationHistoryItem:
    return FiscalPublicationHistoryItem(
        id=1,
        external_id=external_id,
        source="SVRS",
        title=title,
        document_type="SCHEMA",
        published_at=datetime(2026, 1, 1, 0, 0),
        download_url=url(filename),
        document=FiscalPublicationHistoryDocument(
            content_text=criar_content_text(content or schema_sem_obs()),
            content_hash="a" * 64,
            content_length=10,
            extraction_status="EXTRACTED",
            extractor_version="svrs-schema-zip-v1",
            extracted_at=datetime(2026, 1, 1, 1, 0)
        )
    )


def criar_documento(content: str) -> PublicationDocument:
    return PublicationDocument(
        source_url="https://example.com/schema.zip",
        content_text=criar_content_text(content),
        content_hash="b" * 64,
        content_length=len(content),
        extraction_status="EXTRACTED",
        extractor_version="svrs-schema-zip-v1",
        extracted_at=datetime(2026, 1, 2, 1, 0)
    )


def criar_content_text(content: str) -> str:
    return f"=== arquivo: schema.xsd ===\n{content}"


def schema_sem_obs() -> str:
    return (
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        "<xs:complexType name=\"TNFe\">"
        "<xs:sequence>"
        "<xs:element name=\"infNFe\" type=\"TInfNFe\"/>"
        "</xs:sequence>"
        "</xs:complexType>"
        "</xs:schema>"
    )


def schema_com_obs() -> str:
    return (
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        "<xs:complexType name=\"TNFe\">"
        "<xs:sequence>"
        "<xs:element name=\"infNFe\" type=\"TInfNFe\"/>"
        "<xs:element name=\"obs\" type=\"TObs\"/>"
        "</xs:sequence>"
        "</xs:complexType>"
        "</xs:schema>"
    )


def url(filename: str) -> str:
    return (
        "https://dfe-portal.svrs.rs.gov.br/NFE/"
        "DownloadArquivoEstatico/?sistema=NFE&tipoArquivo=2"
        f"&nomeArquivo={filename}"
    )
