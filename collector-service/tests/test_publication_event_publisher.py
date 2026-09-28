from datetime import datetime

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
