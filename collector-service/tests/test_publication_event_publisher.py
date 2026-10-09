from datetime import datetime
from unittest.mock import Mock
import pika
import pytest
import subprocess

from app.messaging import publication_event_publisher
from app.model.publication import Publication
from app.model.publication_event import PublicationEvent


class FakeChannel:
    def __init__(self):
        self.exchange_declare_calls = []
        self.basic_publish_calls = []
        self.queue_declare_calls = []
        self.queue_bind_calls = []

    def exchange_declare(self, **kwargs):
        self.exchange_declare_calls.append(kwargs)

    def confirm_delivery(self):
        self.confirmed = True

    def basic_publish(self, **kwargs):
        self.basic_publish_calls.append(kwargs)

    def queue_declare(self, **kwargs):
        self.queue_declare_calls.append(kwargs)

    def queue_bind(self, **kwargs):
        self.queue_bind_calls.append(kwargs)


class FakeConnection:
    def __init__(self, channel):
        self.channel_instance = channel
        self.closed = False

    def channel(self):
        return self.channel_instance

    def close(self):
        self.closed = True


def test_deve_publicar_no_exchange_sem_declarar_fila_ou_binding(monkeypatch):
    channel = FakeChannel()
    connection = FakeConnection(channel)

    monkeypatch.setattr(
        publication_event_publisher,
        "create_rabbitmq_connection",
        lambda: connection
    )

    publication = Publication(
        external_id="a" * 64,
        source="SVRS",
        title="Nota Técnica 2026.009 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        download_url="https://example.com/nota.pdf"
    )

    event = PublicationEvent(
        event_type="publication.discovered",
        occurred_at=datetime.now(),
        publication=publication
    )

    publication_event_publisher.publish_publication_event(event)

    assert channel.exchange_declare_calls == [{
        "exchange": "fiscalwatch.publications",
        "exchange_type": "topic",
        "durable": True
    }]

    assert channel.queue_declare_calls == []
    assert channel.queue_bind_calls == []

    assert len(channel.basic_publish_calls) == 1
    assert channel.basic_publish_calls[0]["exchange"] == (
        "fiscalwatch.publications"
    )
    assert channel.basic_publish_calls[0]["routing_key"] == (
        "publication.discovered"
    )
    assert channel.basic_publish_calls[0]["body"] == event.model_dump_json()
    assert connection.closed is True
    assert channel.confirmed is True
    assert channel.basic_publish_calls[0]["mandatory"] is True


@pytest.mark.parametrize("error", [RuntimeError("envio falhou"),
    pika.exceptions.NackError([]), pika.exceptions.UnroutableError([]), TimeoutError()])
def test_falha_nack_return_timeout_sao_propagados_e_conexao_fecha(monkeypatch, error):
    connection = Mock()
    connection.channel.return_value.basic_publish.side_effect = error
    monkeypatch.setattr(publication_event_publisher, "create_rabbitmq_connection",
                        lambda **kwargs: connection)
    event = Mock()
    event.model_dump_json.return_value = "{}"
    with pytest.raises(type(error)):
        publication_event_publisher.publish_publication_event(event)
    connection.channel.return_value.confirm_delivery.assert_called_once()
    assert connection.channel.return_value.basic_publish.call_args.kwargs["mandatory"]
    connection.close.assert_called_once()


def test_prazo_do_worker_propaga_timeout_sem_sucesso(monkeypatch):
    execute = Mock(side_effect=subprocess.TimeoutExpired("worker", 2))
    monkeypatch.setattr(publication_event_publisher.subprocess, "run", execute)
    event = Mock()
    event.model_dump_json.return_value = "{}"
    with pytest.raises(subprocess.TimeoutExpired):
        publication_event_publisher.publish_publication_event(event, timeout=2)
    assert execute.call_args.kwargs["timeout"] == 2
    assert execute.call_args.kwargs["input"] == "{}"
    assert "env" not in execute.call_args.kwargs  # herda o broker configurado


@pytest.mark.parametrize("code,error", [(2, pika.exceptions.NackError),
    (3, pika.exceptions.UnroutableError), (4, publication_event_publisher.PublicationConnectionError),
    (1, RuntimeError)])
def test_worker_nao_registra_sucesso_em_falha(monkeypatch, code, error):
    monkeypatch.setattr(publication_event_publisher.subprocess, "run",
                        Mock(return_value=Mock(returncode=code)))
    with pytest.raises(error):
        publication_event_publisher.publish_publication_event(Mock(), timeout=2)


def test_worker_confirmado_retorna_sem_republicar_no_processo_pai(monkeypatch):
    monkeypatch.setattr(publication_event_publisher.subprocess, "run",
                        Mock(return_value=Mock(returncode=0)))
    connection = Mock()
    monkeypatch.setattr(publication_event_publisher, "create_rabbitmq_connection", connection)
    publication_event_publisher.publish_publication_event(Mock(), timeout=2)
    connection.assert_not_called()
