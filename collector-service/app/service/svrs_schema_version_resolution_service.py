from app.model.svrs_schema_identity import (
    SvrsSchemaPackageIdentity,
    SvrsSchemaPreviousVersionResolution
)


RESOLVED = "RESOLVED"
NOT_FOUND = "NOT_FOUND"
AMBIGUOUS = "AMBIGUOUS"


def resolve_previous_svrs_schema_version(
    current_identity: SvrsSchemaPackageIdentity,
    known_identities: list[SvrsSchemaPackageIdentity]
) -> SvrsSchemaPreviousVersionResolution:
    if not _is_resolved_identity(current_identity):
        return SvrsSchemaPreviousVersionResolution(
            status=NOT_FOUND
        )

    current_key = _version_key(current_identity)
    candidates_by_version = {}

    for identity in known_identities:
        if not _is_resolved_identity(identity):
            continue

        if identity.family_id != current_identity.family_id:
            continue

        candidate_key = _version_key(identity)

        if candidate_key >= current_key:
            continue

        candidates_by_version.setdefault(candidate_key, []).append(identity)

    if not candidates_by_version:
        return SvrsSchemaPreviousVersionResolution(
            status=NOT_FOUND
        )

    previous_key = max(candidates_by_version)
    previous_candidates = candidates_by_version[previous_key]

    if len(previous_candidates) > 1:
        return SvrsSchemaPreviousVersionResolution(
            status=AMBIGUOUS,
            candidate_identities=previous_candidates
        )

    return SvrsSchemaPreviousVersionResolution(
        status=RESOLVED,
        previous_identity=previous_candidates[0],
        candidate_identities=previous_candidates
    )


def _is_resolved_identity(identity: SvrsSchemaPackageIdentity) -> bool:
    return (
        identity.status == RESOLVED
        and identity.family_id is not None
        and identity.version_parts is not None
    )


def _version_key(
    identity: SvrsSchemaPackageIdentity
) -> tuple[tuple[int, ...], tuple[int, str]]:
    return (
        tuple(identity.version_parts or []),
        _suffix_key(identity.version_suffix)
    )


def _suffix_key(suffix: str | None) -> tuple[int, str]:
    if suffix is None:
        return 0, ""

    return 1, suffix
