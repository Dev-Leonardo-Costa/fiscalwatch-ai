package br.com.fiscalwatch.fiscalservice.publication.repository;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentSnapshotEntity;

public interface PublicationDocumentSnapshotAppend {
    PublicationDocumentSnapshotEntity append(PublicationDocumentSnapshotEntity snapshot);
}
