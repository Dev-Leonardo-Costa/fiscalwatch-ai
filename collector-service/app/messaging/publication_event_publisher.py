import pika
import subprocess
import sys
from pathlib import Path

from app.messaging.rabbitmq_connection import create_rabbitmq_connection
from app.model.publication_event import PublicationEvent


EXCHANGE_NAME = "fiscalwatch.publications"
ROUTING_KEY = "publication.discovered"


class PublicationConnectionError(RuntimeError):
    """Conexão falhou antes de qualquer envio."""


def publish_publication_event(event: PublicationEvent, *, timeout: float | None = None) -> None:
    if timeout is not None:
        # BlockingConnection.call_later não dispara durante a espera por confirm.
        # O processo filho usa este mesmo publisher; timeout encerra seus sockets.
        completed = subprocess.run(
            [sys.executable, "-m", "app.messaging.confirmed_publication"],
            input=event.model_dump_json(), text=True, encoding="utf-8", capture_output=True,
            timeout=timeout, cwd=Path(__file__).resolve().parents[2],
            creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0,
        )
        if completed.returncode == 2:
            raise pika.exceptions.NackError([])
        if completed.returncode == 3:
            raise pika.exceptions.UnroutableError([])
        if completed.returncode == 4:
            raise PublicationConnectionError("Conexão RabbitMQ não aberta")
        if completed.returncode != 0:
            raise RuntimeError("Publicação RabbitMQ não confirmada")
        return
    try:
        connection = create_rabbitmq_connection()
    except Exception as error:
        raise PublicationConnectionError("Conexão RabbitMQ não aberta") from error

    try:
        channel = connection.channel()

        channel.exchange_declare(
            exchange=EXCHANGE_NAME,
            exchange_type="topic",
            durable=True
        )

        channel.confirm_delivery()
        message = event.model_dump_json()

        confirmed = channel.basic_publish(
            exchange=EXCHANGE_NAME,
            routing_key=ROUTING_KEY,
            body=message,
            mandatory=True,
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2
            )
        )
        if confirmed is False:
            raise RuntimeError("Publicação RabbitMQ não confirmada")

    finally:
        connection.close()
