from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class FiscalPublicationHistoryDocument(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    content_text: str = Field(alias="contentText")
    content_hash: str = Field(alias="contentHash")
    content_length: int = Field(alias="contentLength")
    extraction_status: str = Field(alias="extractionStatus")
    extractor_version: str = Field(alias="extractorVersion")
    extracted_at: datetime = Field(alias="extractedAt")


class FiscalPublicationHistoryItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    external_id: str = Field(alias="externalId")
    source: str
    title: str
    document_type: str = Field(alias="documentType")
    published_at: datetime = Field(alias="publishedAt")
    download_url: Optional[str] = Field(alias="downloadUrl")
    document: FiscalPublicationHistoryDocument
