from app.service.document_type_service import normalize_document_type


def test_deve_classificar_ato_tecnico_pelo_titulo():
    resultado = normalize_document_type(
        document_type="Ato",
        title="ATO TÉCNICO CONJUNTO RFB/SUARA/CGIBS Nº 4"
    )

    assert resultado == "ATO_TECNICO"


def test_nao_deve_classificar_ato_generico_como_ato_tecnico():
    resultado = normalize_document_type(
        document_type="Ato",
        title="ATO CONJUNTO Nº 10"
    )

    assert resultado == "OUTRO"