package br.com.fiscalwatch.fiscalservice.publication.dto;

import java.time.LocalDateTime;

public record PublicationSnapshotResponse(Long snapshotId, Long publicationId, Long revisionId,
        String family, Integer year, Integer number, String declaredVersion, String contentHash,
        String source, String sourceUrl, String title, LocalDateTime capturedAt, String contentText) { }
