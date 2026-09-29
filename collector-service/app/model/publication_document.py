from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class PublicationDocument(BaseModel):
    source_url: Optional[str] = None
    content_text: Optional[str] = None
    content_hash: Optional[str] = None
    content_length: Optional[int] = None
    extraction_status: str
    extraction_error: Optional[str] = None
    extractor_version: Optional[str] = None
    extracted_at: Optional[datetime] = None
