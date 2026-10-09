package br.com.fiscalwatch.fiscalservice.publication.dto;

import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;

/** Estado compacto para coleta seletiva; não expõe texto nem erro de extração. */
public record PublicationCollectionState(String externalId, boolean exists,
        ExtractionStatus extractionStatus, boolean validDocument) {}
