from datetime import datetime
from unittest.mock import Mock

import pytest

from app.model.publication import Publication
from app.model.publication_document import PublicationDocument
from app.service import publication_dispatch_service as dispatch


@pytest.mark.parametrize("status", ["FAILED", "PENDING", "EMPTY"])
def test_recuperacao_invalida_nao_publica_nem_aciona_schema(monkeypatch, status):
    extract, publish, schema = Mock(), Mock(), Mock()
    extract.return_value = PublicationDocument(extraction_status=status)
    monkeypatch.setattr(dispatch, "extract_publication_document", extract)
    monkeypatch.setattr(dispatch, "publish_publication_event", publish)
    monkeypatch.setattr(dispatch, "_try_dispatch_svrs_schema_impact_analysis", schema)
    publication = Publication(external_id="a" * 64, source="SVRS", title="Fixture",
                              published_at=datetime(2026, 1, 1))
    result = dispatch.dispatch_publication(publication, timeout=12,
                                          analyze_schema=False, recover_only=True)
    assert result["published"] == 0
    extract.assert_called_once_with(publication, timeout=12)
    publish.assert_not_called()
    schema.assert_not_called()


def test_schema_periodico_nao_analisa_antes_da_persistencia(monkeypatch):
    extract, publish, schema = Mock(), Mock(), Mock()
    extract.return_value = PublicationDocument(extraction_status="EXTRACTED", content_text="Válido")
    monkeypatch.setattr(dispatch, "extract_publication_document", extract)
    monkeypatch.setattr(dispatch, "publish_publication_event", publish)
    monkeypatch.setattr(dispatch, "_try_dispatch_svrs_schema_impact_analysis", schema)
    publication = Publication(external_id="a" * 64, source="SVRS", title="Fixture", document_type="SCHEMA",
                              published_at=datetime(2026, 1, 1))
    assert dispatch.dispatch_publication(publication, timeout=12, analyze_schema=False)["published"] == 1
    assert publish.call_args.kwargs == {"timeout": 12}
    schema.assert_not_called()
