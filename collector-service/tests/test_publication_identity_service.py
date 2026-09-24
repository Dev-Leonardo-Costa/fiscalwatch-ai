from app.service.publication_identity_service import generate_external_id


def test_deve_gerar_mesmo_external_id_para_mesma_publicacao():
    source = "SVRS"
    source_identifier = "NT2026.009_v1.00"

    primeiro_external_id = generate_external_id(
        source=source,
        source_identifier=source_identifier
    )

    segundo_external_id = generate_external_id(
        source=source,
        source_identifier=source_identifier
    )

    assert primeiro_external_id == segundo_external_id
    assert len(primeiro_external_id) == 64


def test_deve_gerar_external_id_diferente_para_fontes_diferentes():
    source_identifier = "NT2026.009_v1.00"

    external_id_svrs = generate_external_id(
        source="SVRS",
        source_identifier=source_identifier
    )

    external_id_portal_nfe = generate_external_id(
        source="PORTAL_NFE",
        source_identifier=source_identifier
    )

    assert external_id_svrs != external_id_portal_nfe