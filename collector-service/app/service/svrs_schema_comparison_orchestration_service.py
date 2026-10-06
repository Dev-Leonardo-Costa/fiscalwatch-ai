from app.model.fiscal_publication_history import (
    FiscalPublicationHistoryItem
)
from app.model.publication import Publication
from app.model.publication_document import PublicationDocument
from app.model.svrs_schema_comparison_orchestration import (
    SvrsSchemaComparisonOrchestrationResult
)
from app.model.svrs_schema_identity import SvrsSchemaPackageIdentity
from app.service.fiscal_service_publication_history_client import (
    fetch_svrs_schema_publication_history
)
from app.service.svrs_schema_artifact_parser_service import (
    parse_svrs_schema_artifacts
)
from app.service.svrs_schema_identity_service import (
    parse_svrs_schema_package_identity
)
from app.service.svrs_schema_version_resolution_service import (
    resolve_previous_svrs_schema_version
)
from app.service.xsd_schema_comparison_service import (
    compare_schema_artifacts
)


COMPARED = "COMPARED"
SKIPPED = "SKIPPED"
RESOLVED = "RESOLVED"
AMBIGUOUS = "AMBIGUOUS"


def compare_current_svrs_schema_publication(
    current_publication: Publication,
    current_document: PublicationDocument,
    fiscal_service_base_url: str | None = None
) -> SvrsSchemaComparisonOrchestrationResult:
    current_identity = parse_svrs_schema_package_identity(
        current_publication.title,
        current_publication.download_url
    )

    if current_identity.status != RESOLVED:
        return _skipped(
            current_publication=current_publication,
            current_identity=current_identity,
            reason="CURRENT_IDENTITY_NOT_RESOLVED"
        )

    history = fetch_svrs_schema_publication_history(
        base_url=fiscal_service_base_url
    )

    if not history:
        return _skipped(
            current_publication=current_publication,
            current_identity=current_identity,
            reason="HISTORY_EMPTY"
        )

    history_identities = [
        _history_identity(item)
        for item in history
        if item.external_id != current_publication.external_id
    ]
    resolution = resolve_previous_svrs_schema_version(
        current_identity,
        history_identities
    )

    if resolution.status == AMBIGUOUS:
        return _skipped(
            current_publication=current_publication,
            current_identity=current_identity,
            reason="PREVIOUS_VERSION_AMBIGUOUS"
        )

    if resolution.previous_identity is None:
        return _skipped(
            current_publication=current_publication,
            current_identity=current_identity,
            reason="PREVIOUS_VERSION_NOT_FOUND"
        )

    previous_item = _find_history_item_by_identity(
        history=history,
        identity=resolution.previous_identity,
        current_external_id=current_publication.external_id
    )

    if previous_item is None:
        return _skipped(
            current_publication=current_publication,
            current_identity=current_identity,
            previous_identity=resolution.previous_identity,
            reason="PREVIOUS_DOCUMENT_NOT_FOUND"
        )

    previous_artifacts = parse_svrs_schema_artifacts(
        previous_item.document.content_text
    )
    current_artifacts = parse_svrs_schema_artifacts(
        current_document.content_text
    )
    changes = compare_schema_artifacts(
        previous_artifacts=previous_artifacts,
        current_artifacts=current_artifacts
    )

    return SvrsSchemaComparisonOrchestrationResult(
        status=COMPARED,
        current_identity=current_identity,
        previous_identity=resolution.previous_identity,
        current_external_id=current_publication.external_id,
        previous_external_id=previous_item.external_id,
        changes=changes,
        total_changes=len(changes)
    )


def _history_identity(
    item: FiscalPublicationHistoryItem
) -> SvrsSchemaPackageIdentity:
    return parse_svrs_schema_package_identity(
        item.title,
        item.download_url
    )


def _find_history_item_by_identity(
    history: list[FiscalPublicationHistoryItem],
    identity: SvrsSchemaPackageIdentity,
    current_external_id: str
) -> FiscalPublicationHistoryItem | None:
    for item in history:
        if item.external_id == current_external_id:
            continue

        if _same_identity(_history_identity(item), identity):
            return item

    return None


def _same_identity(
    first: SvrsSchemaPackageIdentity,
    second: SvrsSchemaPackageIdentity
) -> bool:
    return (
        first.status == second.status
        and first.family_id == second.family_id
        and first.version_parts == second.version_parts
        and first.version_suffix == second.version_suffix
    )


def _skipped(
    current_publication: Publication,
    current_identity: SvrsSchemaPackageIdentity,
    reason: str,
    previous_identity: SvrsSchemaPackageIdentity | None = None
) -> SvrsSchemaComparisonOrchestrationResult:
    return SvrsSchemaComparisonOrchestrationResult(
        status=SKIPPED,
        current_identity=current_identity,
        previous_identity=previous_identity,
        current_external_id=current_publication.external_id,
        changes=[],
        total_changes=0,
        reason=reason
    )
