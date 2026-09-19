from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class Publication(BaseModel):
    source: str
    title: str
    document_type: Optional[str] = None

    published_at: datetime
    modified_at: Optional[datetime] = None

    description: Optional[str] = None
    download_url: Optional[str] = None