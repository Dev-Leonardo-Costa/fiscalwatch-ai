from typing import Optional

from pydantic import BaseModel, Field

from app.model.schema_comparison import SchemaChange
from app.model.svrs_schema_identity import SvrsSchemaPackageIdentity


class SvrsSchemaComparisonOrchestrationResult(BaseModel):
    status: str
    current_identity: SvrsSchemaPackageIdentity
    previous_identity: Optional[SvrsSchemaPackageIdentity] = None
    current_external_id: str
    previous_external_id: Optional[str] = None
    changes: list[SchemaChange] = Field(default_factory=list)
    total_changes: int = 0
    reason: Optional[str] = None
