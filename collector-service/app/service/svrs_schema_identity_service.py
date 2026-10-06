import re
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

from app.model.svrs_schema_identity import SvrsSchemaPackageIdentity


RESOLVED = "RESOLVED"
AMBIGUOUS = "AMBIGUOUS"
UNRESOLVED = "UNRESOLVED"

_NT_PATTERN = re.compile(
    r"(?<![A-Za-z0-9])NT[\s_\-]*(\d{4})[._\-\s]?(\d{2,3})(?![A-Za-z0-9])",
    re.IGNORECASE
)
_VERSION_PATTERN = re.compile(
    r"v\s*\.?\s*(\d+(?:[._-]\d+){0,4}[a-z]?)",
    re.IGNORECASE
)
_PACKAGE_CODE_PATTERN = re.compile(
    r"\b(PL_\d{3}[a-z]?)(?=_|\.|$)",
    re.IGNORECASE
)


@dataclass(frozen=True)
class _VersionCandidate:
    raw: str
    parts: list[int]
    suffix: str | None
    start: int
    is_compact: bool

    def comparable(self) -> tuple[tuple[int, ...], str]:
        return tuple(self.parts), self.suffix or ""


@dataclass(frozen=True)
class _NtCandidate:
    value: str
    start: int


def parse_svrs_schema_package_identity(
    title: str,
    download_url: str | None
) -> SvrsSchemaPackageIdentity:
    filename = _filename_from_download_url(download_url)
    title_nts = _extract_nt_candidates(title)
    filename_nts = _extract_nt_candidates(filename)
    title_versions = _extract_version_candidates(title)
    filename_versions = _extract_filename_package_versions(filename)
    package_code = _extract_package_code(filename)

    if len(title_nts) > 1 or len(title_versions) > 1:
        return _identity(
            status=AMBIGUOUS,
            package_code=package_code
        )

    if title_nts:
        nt = title_nts[0].value
    else:
        if len(filename_nts) > 1:
            return _identity(
                status=AMBIGUOUS,
                package_code=package_code
            )

        if not filename_nts:
            return _identity(
                status=UNRESOLVED,
                package_code=package_code
            )

        nt = filename_nts[0].value

    if _filename_nt_conflicts(nt, filename_nts):
        return _identity(
            nt=nt,
            status=AMBIGUOUS,
            package_code=package_code
        )

    if title_versions:
        version = title_versions[0]

        if _filename_version_conflicts(version, filename_versions):
            return _identity(
                nt=nt,
                status=AMBIGUOUS,
                package_code=package_code
            )
    else:
        compact_filename_versions = [
            candidate
            for candidate in filename_versions
            if candidate.is_compact
        ]

        if len(compact_filename_versions) > 1:
            return _identity(
                nt=nt,
                status=AMBIGUOUS,
                package_code=package_code
            )

        if not compact_filename_versions:
            return _identity(
                nt=nt,
                status=UNRESOLVED,
                package_code=package_code
            )

        version = compact_filename_versions[0]

    if not package_code:
        return _identity(
            nt=nt,
            status=UNRESOLVED,
            package_code=package_code,
            version=version
        )

    return _identity(
        nt=nt,
        version=version,
        status=RESOLVED,
        package_code=package_code
    )


def _identity(
    status: str,
    package_code: str | None = None,
    nt: str | None = None,
    version: _VersionCandidate | None = None
) -> SvrsSchemaPackageIdentity:
    family_id = None

    if status == RESOLVED and nt and package_code:
        family_id = f"SCHEMA_PACKAGE:NT:{nt}:{package_code}"

    return SvrsSchemaPackageIdentity(
        nt=nt,
        version_parts=version.parts if version else None,
        version_suffix=version.suffix if version else None,
        raw_version=version.raw if version else None,
        package_code=package_code,
        family_id=family_id,
        status=status
    )


def _filename_from_download_url(download_url: str | None) -> str:
    if not download_url:
        return ""

    parsed_url = urlparse(download_url)
    query = parse_qs(parsed_url.query)
    filename = query.get("nomeArquivo", [""])[0]

    if not filename:
        filename = Path(parsed_url.path).name

    return unquote(filename)


def _extract_nt_candidates(text: str | None) -> list[_NtCandidate]:
    return [
        _NtCandidate(
            value=f"{match.group(1)}.{match.group(2).zfill(3)}",
            start=match.start()
        )
        for match in _NT_PATTERN.finditer(text or "")
    ]


def _extract_version_candidates(
    text: str | None
) -> list[_VersionCandidate]:
    candidates = []

    for match in _VERSION_PATTERN.finditer(text or ""):
        candidate = _build_version_candidate(match)

        if candidate is not None:
            candidates.append(candidate)

    return candidates


def _extract_filename_package_versions(
    filename: str
) -> list[_VersionCandidate]:
    nt_candidates = _extract_nt_candidates(filename)
    version_candidates = _extract_version_candidates(filename)

    if not nt_candidates:
        return version_candidates

    first_nt_start = nt_candidates[0].start

    return [
        candidate
        for candidate in version_candidates
        if candidate.start > first_nt_start
    ]


def _build_version_candidate(
    match: re.Match
) -> _VersionCandidate | None:
    raw_number = match.group(1)
    raw_version = match.group(0).strip()
    suffix = None

    if raw_number[-1:].isalpha():
        suffix = raw_number[-1].lower()
        raw_number = raw_number[:-1]

    normalized_number = raw_number.replace("_", ".").replace("-", ".")

    if "." in normalized_number:
        parts = [
            int(part)
            for part in normalized_number.split(".")
            if part != ""
        ]
    elif normalized_number.isdigit() and len(normalized_number) == 3:
        parts = [
            int(normalized_number[0]),
            int(normalized_number[1:3])
        ]
    elif normalized_number.isdigit():
        parts = [int(normalized_number)]
    else:
        return None

    return _VersionCandidate(
        raw=raw_version,
        parts=parts,
        suffix=suffix,
        start=match.start(),
        is_compact=(
            raw_number.isdigit()
            and len(raw_number) == 3
            and raw_number == normalized_number
        )
    )


def _extract_package_code(filename: str) -> str | None:
    match = _PACKAGE_CODE_PATTERN.search(filename or "")

    if match:
        prefix, suffix = match.group(1)[:6], match.group(1)[6:]

        return f"{prefix.upper()}{suffix.lower()}"

    return _extract_event_package_code(filename)


def _extract_event_package_code(filename: str) -> str | None:
    stem = Path(filename or "").stem

    if not stem.lower().startswith(("evento_", "eventos_")):
        return None

    stem_without_version = re.sub(
        r"(?i)(?:[_\s-]*v\.?\d+(?:[._-]\d+){0,4}[a-z]?)$",
        "",
        stem
    )
    stem_without_version = re.sub(
        r"(?i)(?:[_\s-]*\d+\.\d+)$",
        "",
        stem_without_version
    )
    stem_without_marker = re.sub(
        r"(?i)(?:_PL)$",
        "",
        stem_without_version
    )
    normalized = _normalize_package_code_words(stem_without_marker)

    if normalized in {"EVENTO", "EVENTOS"}:
        return None

    return normalized


def _normalize_package_code_words(value: str) -> str:
    value = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", value)
    value = re.sub(r"[^A-Za-z0-9]+", "_", value)
    value = re.sub(r"_+", "_", value)

    return value.strip("_").upper()


def _filename_nt_conflicts(
    title_nt: str,
    filename_nts: list[_NtCandidate]
) -> bool:
    if not filename_nts:
        return False

    return title_nt not in [
        candidate.value
        for candidate in filename_nts
    ]


def _filename_version_conflicts(
    title_version: _VersionCandidate,
    filename_versions: list[_VersionCandidate]
) -> bool:
    if not filename_versions:
        return False

    return title_version.comparable() not in [
        candidate.comparable()
        for candidate in filename_versions
    ]
