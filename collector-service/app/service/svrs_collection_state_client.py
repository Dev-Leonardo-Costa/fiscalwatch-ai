import httpx
from pydantic import BaseModel, ConfigDict, Field

from app.service.fiscal_service_publication_history_client import _resolve_base_url


class CollectionState(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid")
    external_id: str = Field(alias="externalId")
    exists: bool
    extraction_status: str | None = Field(alias="extractionStatus")
    valid_document: bool = Field(alias="validDocument")


def fetch_collection_state(external_id: str, *, timeout: float) -> CollectionState:
    response = httpx.get(
        f"{_resolve_base_url(None)}/api/publications/collection-state",
        params={"externalId": external_id}, timeout=timeout,
    )
    response.raise_for_status()
    state = CollectionState.model_validate(response.json())
    if state.external_id != external_id:
        raise ValueError("Identidade inesperada na resposta Java")
    if (not state.exists and (state.extraction_status is not None or state.valid_document)):
        raise ValueError("Estado Java inconsistente")
    if state.valid_document and state.extraction_status != "EXTRACTED":
        raise ValueError("Estado Java inconsistente")
    return state
