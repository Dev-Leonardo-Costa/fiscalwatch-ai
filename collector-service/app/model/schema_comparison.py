from typing import Any, Optional

from pydantic import BaseModel, Field


class SchemaArtifact(BaseModel):
    path: str
    content: str
    content_hash: Optional[str] = None


class XsdTypeDefinition(BaseModel):
    name: str
    artifact: str
    schema_path: str
    base: Optional[str] = None
    patterns: list[str] = Field(default_factory=list)
    enumerations: list[str] = Field(default_factory=list)
    facets: dict[str, str] = Field(default_factory=dict)


class SchemaChange(BaseModel):
    artifact: str
    change_type: str
    schema_path: Optional[str] = None
    symbol_name: Optional[str] = None
    before: Optional[Any] = None
    after: Optional[Any] = None
    before_type_definition: Optional[XsdTypeDefinition] = None
    after_type_definition: Optional[XsdTypeDefinition] = None
