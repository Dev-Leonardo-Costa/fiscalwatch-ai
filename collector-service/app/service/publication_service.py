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
    seen = set()

    for publication in publications:

        if publication.download_url:
            key = (
                publication.source,
                publication.download_url
            )
        else:
            key = (
                publication.source,
                publication.title.strip().lower(),
                publication.published_at
            )

        if key not in seen:
            seen.add(key)
            unique_publications.append(publication)

    return unique_publications


def find_duplicate_publications(
    publications: list[Publication]
) -> list[Publication]:

    duplicates = []
    seen = set()

    for publication in publications:

        if publication.download_url:
            key = (
                publication.source,
                publication.download_url
            )
        else:
            key = (
                publication.source,
                publication.title.strip().lower(),
                publication.published_at
            )

        if key in seen:
            duplicates.append(publication)
        else:
            seen.add(key)

    return duplicates