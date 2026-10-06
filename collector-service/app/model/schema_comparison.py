from typing import Any, Optional

from pydantic import BaseModel


class SchemaArtifact(BaseModel):
    path: str
    content: str
    content_hash: Optional[str] = None


class SchemaChange(BaseModel):
    artifact: str
    change_type: str
    schema_path: Optional[str] = None
    symbol_name: Optional[str] = None
    before: Optional[Any] = None
    after: Optional[Any] = None
