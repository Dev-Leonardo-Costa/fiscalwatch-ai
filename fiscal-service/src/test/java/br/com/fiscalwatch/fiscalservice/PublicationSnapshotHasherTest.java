package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationDocumentEvent;
import br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class PublicationSnapshotHasherTest {
    private static final LocalDateTime DATE = LocalDateTime.of(2026, 10, 9, 10, 0);

    @Test
    void deveSerDeterministicoEOrdenarCamposComComprimentosUtf8() {
        var result = hash(publication("Título", null), document("abc", null));
        assertEquals(result, hash(publication("Título", null), document("abc", null)));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.contentHash());
        String canonical = "fiscalwatch.publication-snapshot:v1\n"
                + "description:-1:\n"
                + "document_content_hash:64:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\n"
                + "document_source_url:17:https://teste/doc\n"
                + "document_type:-1:\n"
                + "download_url:17:https://teste/doc\n"
                + "external_id:2:id\n"
                + "modified_at:-1:\n"
                + "published_at:29:2026-10-09T10:00:00.000000000\n"
                + "source:4:SVRS\n"
                + "title:7:Título\n";
        assertEquals(canonical, result.canonicalSnapshot());
        assertEquals(PublicationSnapshotHasher.calculateContentHash(canonical), result.snapshotHash());
    }

    @Test
    void deveEquivalerHashAusenteDeclaradoEMaiusculo() {
        var absent = hash(publication("Título", null), document("abc", null));
        assertEquals(absent, hash(publication("Título", null), document("abc", absent.contentHash())));
        assertEquals(absent, hash(publication("Título", null), document("abc", absent.contentHash().toUpperCase(Locale.ROOT))));
    }

    @Test
    void deveAlterarSnapshotQuandoConteudoOuMetadadosMudarem() {
        var original = hash(publication("Título", "Descrição"), document("abc", null));
        assertNotEquals(original.snapshotHash(), hash(publication("Título", "Descrição"), document("abcd", null)).snapshotHash());
        assertNotEquals(original.snapshotHash(), hash(publication("Outro", "Descrição"), document("abc", null)).snapshotHash());
        var metadata = hash(publication("Título", "Outra"), document("abc", null));
        assertNotEquals(original.snapshotHash(), metadata.snapshotHash());
        assertEquals(original.contentHash(), metadata.contentHash());
    }

    @Test
    void deveIncluirIdentidadeTipoDatasEUrlsNoSnapshot() {
        var base = publication("Título", null);
        var original = hash(base, null).snapshotHash();
        PublicationRequest[] changes = {
                new PublicationRequest("outro", base.source(), base.title(), null, DATE, null, null, base.downloadUrl()),
                new PublicationRequest(base.externalId(), "OUTRA", base.title(), null, DATE, null, null, base.downloadUrl()),
                new PublicationRequest(base.externalId(), base.source(), base.title(), br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType.SCHEMA, DATE, null, null, base.downloadUrl()),
                new PublicationRequest(base.externalId(), base.source(), base.title(), null, DATE.plusSeconds(1), null, null, base.downloadUrl()),
                new PublicationRequest(base.externalId(), base.source(), base.title(), null, DATE, DATE, null, base.downloadUrl()),
                new PublicationRequest(base.externalId(), base.source(), base.title(), null, DATE, null, null, "https://outra")
        };
        for (var change : changes) assertNotEquals(original, hash(change, null).snapshotHash());
        var doc = document("abc", null);
        var otherUrl = new PublicationDocumentEvent("https://outra", "abc", null, null, ExtractionStatus.EXTRACTED, null, null, null);
        assertNotEquals(hash(base, doc).snapshotHash(), hash(base, otherUrl).snapshotHash());
    }

    @Test
    void deveIgnorarMetadadosVolateisDaExtracao() {
        var original = document("abc", null);
        var changed = new PublicationDocumentEvent(original.sourceUrl(), "abc", null, 999,
                ExtractionStatus.EXTRACTED, null, "outro-extrator", DATE.plusYears(1));
        assertEquals(hash(publication("Título", null), original), hash(publication("Título", null), changed));
        // occurred_at não pertence à entrada deste componente.
    }

    @Test
    void deveDistinguirNuloVazioESeparadoresDentroDosValores() {
        assertNotEquals(hash(publication("Título", null), null).snapshotHash(), hash(publication("Título", ""), null).snapshotHash());
        assertNotEquals(hash(publication("a\ndescription:1:b", "c"), null).snapshotHash(), hash(publication("a", "b\ndescription:1:c"), null).snapshotHash());
        var metadataOnly = hash(publication("Título", null), null);
        assertNull(metadataOnly.contentHash());
        assertEquals(64, metadataOnly.snapshotHash().length());
    }

    @Test
    void devePreservarUnicodeEspacosEQuebrasSemRenormalizacao() {
        // Vetor independente: hashlib.sha256(text.encode('utf-8')).hexdigest().
        assertEquals("fa29912dda0d09d9801f250d6848d04e4e96ee3ef3933c23f380b7dc2f17b3ba",
                PublicationSnapshotHasher.calculateContentHash("IBS: alíquota 🧾\né"));
        assertNotEquals(PublicationSnapshotHasher.calculateContentHash("é"), PublicationSnapshotHasher.calculateContentHash("e\u0301"));
        assertNotEquals(PublicationSnapshotHasher.calculateContentHash("a b"), PublicationSnapshotHasher.calculateContentHash("a  b"));
        assertNotEquals(PublicationSnapshotHasher.calculateContentHash("a\nb"), PublicationSnapshotHasher.calculateContentHash("a\r\nb"));
        assertNotEquals(hash(publication("é", null), null).snapshotHash(), hash(publication("e\u0301", null), null).snapshotHash());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "inválido", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void deveRejeitarHashMalformadoOuInconsistente(String hash) {
        assertThrows(IllegalArgumentException.class, () -> hash(publication("Título", null), document("abc", hash)));
    }

    @ParameterizedTest
    @EnumSource(value = ExtractionStatus.class, names = {"FAILED", "PENDING", "EMPTY"})
    void deveRejeitarEstadosSemConteudoValidoMesmoComHash(ExtractionStatus status) {
        var doc = new PublicationDocumentEvent(null, "abc", PublicationSnapshotHasher.calculateContentHash("abc"), 3, status, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> hash(publication("Título", null), doc));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void deveRejeitarExtractedSemTextoValido(String text) {
        assertThrows(IllegalArgumentException.class, () -> hash(publication("Título", null), document(text, null)));
    }

    @Test
    void deveRejeitarExtractedComErroOuEstadoAusente() {
        var error = new PublicationDocumentEvent(null, "abc", null, null, ExtractionStatus.EXTRACTED, "erro", null, null);
        var noStatus = new PublicationDocumentEvent(null, "abc", null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> hash(publication("Título", null), error));
        assertThrows(IllegalArgumentException.class, () -> hash(publication("Título", null), noStatus));
    }

    @Test
    void deveRejeitarUnicodeInvalidoSemSubstituicaoSilenciosa() {
        assertThrows(IllegalArgumentException.class, () -> PublicationSnapshotHasher.calculateContentHash("\ud800"));
        assertThrows(IllegalArgumentException.class, () -> PublicationSnapshotHasher.calculateContentHash("\udc00"));
        assertThrows(IllegalArgumentException.class, () -> hash(publication("\ud800", null), null));
    }

    private static PublicationRequest publication(String title, String description) {
        return new PublicationRequest("id", "SVRS", title, null, DATE, null, description, "https://teste/doc");
    }

    private static PublicationDocumentEvent document(String text, String hash) {
        return new PublicationDocumentEvent("https://teste/doc", text, hash, null, ExtractionStatus.EXTRACTED, null, "v1", DATE);
    }

    private static PublicationSnapshotHasher.SnapshotHash hash(PublicationRequest publication, PublicationDocumentEvent document) {
        return PublicationSnapshotHasher.calculate(publication, document);
    }
}
