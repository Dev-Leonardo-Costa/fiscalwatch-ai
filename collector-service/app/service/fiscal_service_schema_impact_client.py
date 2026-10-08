import httpx

from app.model.schema_comparison import SchemaChange, XsdTypeDefinition
from app.model.svrs_schema_comparison_orchestration import (
    SvrsSchemaComparisonOrchestrationResult
)
from app.model.svrs_schema_identity import SvrsSchemaPackageIdentity
from app.service.fiscal_service_publication_history_client import (
    _resolve_base_url
)


COMPARED = "COMPARED"
FISCAL_SERVICE_SCHEMA_IMPACT_TIMEOUT_SECONDS = 10.0


def send_schema_comparison_impact_analysis(
    comparison: SvrsSchemaComparisonOrchestrationResult,
    base_url: str | None = None
) -> dict | None:
    if comparison.status != COMPARED:
        return None

    resolved_base_url = _resolve_base_url(base_url)
    response = httpx.post(
        f"{resolved_base_url}/api/impact-analyses/schema-comparisons",
        json=_payload(comparison),
        timeout=FISCAL_SERVICE_SCHEMA_IMPACT_TIMEOUT_SECONDS
    )
    response.raise_for_status()

    return response.json()


def _payload(comparison: SvrsSchemaComparisonOrchestrationResult) -> dict:
    return {
        "currentExternalId": comparison.current_external_id,
        "previousExternalId": comparison.previous_external_id,
        "currentVersion": _version(comparison.current_identity),
        "previousVersion": _version(comparison.previous_identity),
        "changes": [
            _change_payload(change)
            for change in comparison.changes
        ]
    }


def _version(identity: SvrsSchemaPackageIdentity | None) -> str | None:
    if identity is None:
        return None

    parts = [
        part
        for part in [identity.nt, identity.raw_version]
        if part
    ]

    if not parts:
        return None

    return " ".join(parts)


def _change_payload(change: SchemaChange) -> dict:
    return {
        "artifact": change.artifact,
        "changeType": change.change_type,
        "schemaPath": change.schema_path,
        "symbolName": change.symbol_name,
        "before": change.before,
        "after": change.after,
        "beforeTypeDefinition": _type_definition_payload(
            change.before_type_definition
        ),
        "afterTypeDefinition": _type_definition_payload(
            change.after_type_definition
        )
    }


def _type_definition_payload(
    definition: XsdTypeDefinition | None
) -> dict | None:
    if definition is None:
        return None

    return {
        "name": definition.name,
        "artifact": definition.artifact,
        "schemaPath": definition.schema_path,
        "base": definition.base,
        "patterns": definition.patterns,
        "enumerations": definition.enumerations,
        "facets": definition.facets
    }
