import hashlib
import re

from app.model.schema_comparison import SchemaArtifact


_ARTIFACT_MARKER_PATTERN = re.compile(
    r"^=== arquivo: (.+) ===$"
)
_SVRS_PACKAGE_ROOT_PATTERN = re.compile(
    r"^PL_\d{3}[a-z]?_.*_v\d+(?:[._-]\d+){0,4}[a-z]?$",
    re.IGNORECASE
)


def parse_svrs_schema_artifacts(
    content_text: str | None
) -> list[SchemaArtifact]:
    if not content_text:
        return []

    artifacts = []
    current_path = None
    current_lines = []

    for line in content_text.splitlines():
        marker = _ARTIFACT_MARKER_PATTERN.match(line)

        if marker:
            _append_artifact(
                artifacts=artifacts,
                path=current_path,
                lines=current_lines
            )
            current_path = marker.group(1)
            current_lines = []
            continue

        if current_path is not None:
            current_lines.append(line)

    _append_artifact(
        artifacts=artifacts,
        path=current_path,
        lines=current_lines
    )

    return artifacts


def _append_artifact(
    artifacts: list[SchemaArtifact],
    path: str | None,
    lines: list[str]
) -> None:
    if path is None:
        return

    content = "\n".join(lines).strip()

    if not content:
        return

    artifacts.append(
        SchemaArtifact(
            path=_logical_artifact_path(path),
            content=content,
            content_hash=hashlib.sha256(
                content.encode("utf-8")
            ).hexdigest()
        )
    )


def _logical_artifact_path(path: str) -> str:
    normalized_path = path.replace("\\", "/")

    if "/" not in normalized_path:
        return normalized_path

    root, relative_path = normalized_path.split("/", 1)

    if _SVRS_PACKAGE_ROOT_PATTERN.match(root):
        return relative_path

    return normalized_path
