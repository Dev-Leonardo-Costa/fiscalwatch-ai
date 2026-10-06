from typing import Optional

from pydantic import BaseModel, Field


class SvrsSchemaPackageIdentity(BaseModel):
    nt: Optional[str] = None
    version_parts: Optional[list[int]] = None
    version_suffix: Optional[str] = None
    raw_version: Optional[str] = None
    package_code: Optional[str] = None
    family_id: Optional[str] = None
    status: str


class SvrsSchemaPreviousVersionResolution(BaseModel):
    status: str
    previous_identity: Optional[SvrsSchemaPackageIdentity] = None
    candidate_identities: list[SvrsSchemaPackageIdentity] = Field(
        default_factory=list
    )
