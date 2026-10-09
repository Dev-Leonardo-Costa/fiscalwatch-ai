"""Worker de um único envio; iniciado explicitamente pelo publisher com prazo."""
import logging
import sys
import pika

from app.messaging.publication_event_publisher import (
    PublicationConnectionError, publish_publication_event,
)
from app.model.publication_event import PublicationEvent


def main():
    # Falhas são comunicadas por código, nunca por payload/credenciais em stderr.
    logging.disable(logging.CRITICAL)
    try:
        event = PublicationEvent.model_validate_json(sys.stdin.buffer.read().decode("utf-8"))
        publish_publication_event(event)
        return 0
    except pika.exceptions.NackError:
        return 2
    except pika.exceptions.UnroutableError:
        return 3
    except PublicationConnectionError:
        return 4
    except Exception:
        return 1


if __name__ == "__main__":
    sys.exit(main())
