from datetime import datetime, timedelta

from app.model.publication import Publication


def filter_recent_publications(
    publications: list[Publication],
    hours: int = 72
) -> list[Publication]:

    recent_publications = []

    for publication in publications:

        reference_date = (
            publication.modified_at
            if publication.modified_at
            else publication.published_at
        )

        if reference_date.tzinfo is not None:
            now = datetime.now(reference_date.tzinfo)
        else:
            now = datetime.now()

        limit_datetime = now - timedelta(hours=hours)

        if reference_date >= limit_datetime:
            recent_publications.append(publication)

    return recent_publications


def merge_publications(
    *publication_lists: list[Publication]
) -> list[Publication]:

    publications = []

    for publication_list in publication_lists:
        publications.extend(publication_list)

    return publications


def remove_duplicate_publications(
    publications: list[Publication]
) -> list[Publication]:

    unique_publications = []
    seen_external_ids = set()

    for publication in publications:

        if publication.external_id in seen_external_ids:
            continue

        seen_external_ids.add(publication.external_id)
        unique_publications.append(publication)

    return unique_publications


def find_duplicate_publications(
    publications: list[Publication]
) -> list[Publication]:

    duplicates = []
    seen_external_ids = set()

    for publication in publications:

        if publication.external_id in seen_external_ids:
            duplicates.append(publication)
            continue

        seen_external_ids.add(publication.external_id)

    return duplicates


def is_relevant_publication(
    publication: Publication
) -> bool:

    relevant_terms = [
        "reforma tributária",
        "cbs",
        "imposto seletivo",
        "imposto sobre bens e serviços",
        "nf-e",
        "nfc-e",
        "dfe",
        "df-e",
        "nota técnica",
        "split payment",
        "schema",
        "xml",
        "xsd",
        "regra de validação",
        "ato técnico conjunto",
        "api fiscal",
        "cgibs"
    ]

    text = (
        f"{publication.title} "
        f"{publication.description or ''}"
    ).lower()

    return any(
        term in text
        for term in relevant_terms
    )


def filter_relevant_publications(
    publications: list[Publication]
) -> list[Publication]:

    return [
        publication
        for publication in publications
        if is_relevant_publication(publication)
    ]
