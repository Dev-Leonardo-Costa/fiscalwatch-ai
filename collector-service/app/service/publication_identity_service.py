import hashlib


def generate_external_id(source: str, source_identifier: str) -> str:
    raw_identity = f"{source}:{source_identifier.strip()}"

    return hashlib.sha256(
        raw_identity.encode("utf-8")
    ).hexdigest()