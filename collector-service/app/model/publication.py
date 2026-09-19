from datetime import date
from typing import Optional

from pydantic import BaseModel


class Publication(BaseModel):
    source: str
    title: str
    document_type: Optional[str] = None
    published_at: date
    description: Optional[str] = None
    download_url: Optional[str] = None