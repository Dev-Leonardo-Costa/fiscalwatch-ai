from datetime import datetime
from typing import Optional

from pydantic import BaseModel
from app.model.publication_document import PublicationDocument
from app.model.publication import Publication

PUBLICATION_DISCOVERED = "publication.discovered"


class PublicationEvent(BaseModel):
    event_type: str
    occurred_at: datetime
    publication: Publication
    document: Optional[PublicationDocument] = None
