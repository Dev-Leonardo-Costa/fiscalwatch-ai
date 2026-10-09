package br.com.fiscalwatch.fiscalservice.impactanalysis.service;

import br.com.fiscalwatch.fiscalservice.impactanalysis.exception.InvalidPublicationDocumentException;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher;

/** Mesma regra de integridade para o acionamento manual e o job. */
public final class PublicationDocumentValidator {
    private PublicationDocumentValidator() { }

    public static void validate(PublicationDocumentEntity document) {
        String text = document.getContentText();
        String hash = document.getContentHash();
        if (document.getExtractionStatus() != ExtractionStatus.EXTRACTED
                || text == null || text.isBlank() || document.getExtractionError() != null
                || hash == null || !hash.matches("[0-9a-fA-F]{64}")
                || document.getContentLength() == null
                // Python len(text) conta code points, não unidades UTF-16.
                || document.getContentLength() != text.codePointCount(0, text.length())) {
            throw new InvalidPublicationDocumentException();
        }
        try {
            if (!PublicationSnapshotHasher.calculateContentHash(text).equalsIgnoreCase(hash)) {
                throw new InvalidPublicationDocumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidPublicationDocumentException();
        }
    }
}
