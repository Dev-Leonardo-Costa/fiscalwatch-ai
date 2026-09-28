from datetime import datetime

from app.collectors.svrs_collector import get_publications
from app.messaging.publication_event_publisher import publish_publication_event
from app.model.publication_event import (
    PUBLICATION_DISCOVERED,
    PublicationEvent
)


def dispatch_svrs_publications() -> dict:
    publications = get_publications()

    published = 0

    for publication in publications:
        event = PublicationEvent(
            event_type=PUBLICATION_DISCOVERED,
            occurred_at=datetime.now(),
            publication=publication
        )

        publish_publication_event(event)
        published += 1

    return {
        "source": "SVRS",
        "collected": len(publications),
        "published": published
    }
