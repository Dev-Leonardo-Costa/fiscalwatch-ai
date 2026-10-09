import io
from datetime import datetime
from types import SimpleNamespace
from unittest.mock import Mock

import pika
import pytest

from app.messaging import confirmed_publication as worker
from app.messaging.publication_event_publisher import PublicationConnectionError
from app.model.publication_event import PublicationEvent
from app.model.publication import Publication


@pytest.mark.parametrize("error,code", [(None, 0), (pika.exceptions.NackError([]), 2),
    (pika.exceptions.UnroutableError([]), 3), (PublicationConnectionError(), 4), (RuntimeError(), 1)])
def test_worker_envia_uma_vez_preserva_unicode_e_codifica_resultado(monkeypatch, error, code):
    event = PublicationEvent(event_type="publication.discovered", occurred_at=datetime(2026, 1, 1),
        publication=Publication(external_id="a" * 64, source="SVRS", title="Título fiscal 漢字 😀",
                                published_at=datetime(2026, 1, 1)))
    monkeypatch.setattr(worker.sys, "stdin", SimpleNamespace(
        buffer=io.BytesIO(event.model_dump_json().encode("utf-8"))))
    publish = Mock(side_effect=error)
    monkeypatch.setattr(worker, "publish_publication_event", publish)
    monkeypatch.setattr(worker.logging, "disable", Mock())
    assert worker.main() == code
    publish.assert_called_once_with(event)
