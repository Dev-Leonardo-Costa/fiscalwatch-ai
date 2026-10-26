package br.com.fiscalwatch.fiscalservice.publication.service;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationSnapshotResponse;
import br.com.fiscalwatch.fiscalservice.publication.entity.*;
import br.com.fiscalwatch.fiscalservice.publication.repository.*;
import br.com.fiscalwatch.fiscalservice.publication.versioning.TechnicalNoteIdentity;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.PublicationDocumentValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDateTime;
import java.util.List;

/** Called under the publication lock and the caller's transaction. */
@Service
@RequiredArgsConstructor
public class PublicationSnapshotService {
    private final PublicationDocumentSnapshotRepository snapshots;
    private final PublicationSnapshotReceiptRepository receipts;
    private final TechnicalNoteRepository notes;
    private final TechnicalNoteRevisionRepository revisions;

    @Transactional(propagation = Propagation.MANDATORY)
    public PublicationDocumentSnapshotEntity capture(PublicationEntity publication,
            PublicationRequest request, PublicationDocumentEntity document) {
        PublicationDocumentValidator.validate(document);
        var identity = TechnicalNoteIdentity.resolve(request, document.getContentText());
        var snapshot = snapshots.append(PublicationDocumentSnapshotEntity.create(publication,
                document.getContentText(), document.getSourceUrl(), identity.map(TechnicalNoteIdentity::version).orElse(null),
                document.getExtractorVersion(), document.getExtractedAt(), LocalDateTime.now(java.time.Clock.systemUTC()), null));
        if (receipts.existsById(snapshot.getId())) return snapshot;
        TechnicalNoteRevisionEntity revision = identity.map(key -> {
            var note = notes.findByFamilyAndYearAndNumber(key.family(), key.year(), key.number())
                    .orElseGet(() -> notes.saveAndFlush(new TechnicalNoteEntity(key.family(), key.year(), key.number())));
            return revisions.findByTechnicalNoteIdAndDeclaredVersionAndContentHashAndSource(
                    note.getId(), key.version(), snapshot.getTextHash(), request.source())
                    .orElseGet(() -> revisions.saveAndFlush(new TechnicalNoteRevisionEntity(
                            note, key.version(), snapshot.getTextHash(), request.source())));
        }).orElse(null);
        receipts.saveAndFlush(new PublicationSnapshotReceiptEntity(snapshot.getId(), revision,
                request, LocalDateTime.now(java.time.Clock.systemUTC())));
        return snapshot;
    }

    @Transactional(readOnly = true)
    public List<PublicationSnapshotResponse> history(Long publicationId) {
        return snapshots.findByPublicationIdOrderByIdAsc(publicationId).stream().map(snapshot -> {
            var receipt = receipts.findById(snapshot.getId()).orElse(null);
            var revision = receipt == null ? null : receipt.getRevision();
            var note = revision == null ? null : revision.getTechnicalNote();
            return new PublicationSnapshotResponse(
                    snapshot.getId(), publicationId, revision == null ? null : revision.getId(),
                    note == null ? null : note.getFamily(), note == null ? null : note.getYear(),
                    note == null ? null : note.getNumber(), snapshot.getDeclaredVersion(), snapshot.getTextHash(),
                    receipt == null ? snapshot.getPublication().getSource() : receipt.getSource(),
                    snapshot.getSourceUrl(), receipt == null ? null : receipt.getTitle(),
                    snapshot.getCapturedAt(), snapshot.getContentText());
        }).toList();
    }
}
