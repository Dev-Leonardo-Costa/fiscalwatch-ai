from datetime import datetime
from pydantic import BaseModel
from app.model.publication import Publication

PUBLICATION_DISCOVERED = "publication.discovered"


class PublicationEvent(BaseModel):
    event_type: str
    occurred_at: datetime
    publication: Publication