def normalize_document_type(
    document_type: str | None,
    title: str | None = None
) -> str:

    normalized_title = (title or "").strip().lower()

    if "ato técnico" in normalized_title:
        return "ATO_TECNICO"

    if not document_type:
        return "OUTRO"

    normalized_type = document_type.strip().lower()

    document_type_mapping = {
        "nota técnica": "NOTA_TECNICA",
        "informe técnico": "INFORME_TECNICO",
        "documento técnico": "DOCUMENTO_TECNICO",
        "notícia": "NOTICIA",
        "tabela": "TABELA",
        "ato técnico": "ATO_TECNICO",
        "ato normativo": "ATO_NORMATIVO",
        "orientação": "ORIENTACAO",
        "schema": "SCHEMA",
        "manual": "MANUAL"
    }

    return document_type_mapping.get(
        normalized_type,
        "OUTRO"
    )