import pika

from app.messaging.rabbitmq_connection import create_rabbitmq_connection
from app.model.publication_event import PublicationEvent


EXCHANGE_NAME = "fiscalwatch.publications"
ROUTING_KEY = "publication.discovered"


def publish_publication_event(event: PublicationEvent) -> None:
    connection = create_rabbitmq_connection()

    try:
        channel = connection.channel()

        channel.exchange_declare(
            exchange=EXCHANGE_NAME,
            exchange_type="topic",
            durable=True
        )

        channel.queue_declare(
            queue="fiscal.publication.discovered",
            durable=True
        )

        channel.queue_bind(
            exchange=EXCHANGE_NAME,
            queue="fiscal.publication.discovered",
            routing_key=ROUTING_KEY
        )

        message = event.model_dump_json()

        channel.basic_publish(
            exchange=EXCHANGE_NAME,
            routing_key=ROUTING_KEY,
            body=message,
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2
            )
        )

    finally:
        connection.close()
