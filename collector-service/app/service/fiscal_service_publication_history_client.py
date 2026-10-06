import os

import httpx

from app.model.fiscal_publication_history import (
    FiscalPublicationHistoryItem
)


FISCAL_SERVICE_BASE_URL_ENV = "FISCAL_SERVICE_BASE_URL"
FISCAL_SERVICE_HISTORY_TIMEOUT_SECONDS = 10.0


def fetch_svrs_schema_publication_history(
    base_url: str | None = None
) -> list[FiscalPublicationHistoryItem]:
    resolved_base_url = _resolve_base_url(base_url)
    response = httpx.get(
        f"{resolved_base_url}/api/publications/history",
        params={
            "source": "SVRS",
            "documentType": "SCHEMA"
        },
        timeout=FISCAL_SERVICE_HISTORY_TIMEOUT_SECONDS
    )
    response.raise_for_status()

    return [
        FiscalPublicationHistoryItem.model_validate(item)
        for item in response.json()
    ]


def _resolve_base_url(base_url: str | None) -> str:
    resolved_base_url = base_url or os.getenv(FISCAL_SERVICE_BASE_URL_ENV)

    if not resolved_base_url:
        raise ValueError(
            f"{FISCAL_SERVICE_BASE_URL_ENV} must be configured"
        )

    return resolved_base_url.rstrip("/")
