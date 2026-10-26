package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.versioning.TechnicalNoteIdentity;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class TechnicalNoteIdentityTest {
    private PublicationRequest request(String source, String title, String url) {
        return new PublicationRequest("test", source, title, DocumentType.NOTA_TECNICA,
                LocalDateTime.now(), null, null, url);
    }

    @Test void documentCoverVersionWinsOverStaleTitleWhenUrlIsReused() {
        var key = TechnicalNoteIdentity.resolve(request("SVRS", "NT 2025.002 v1.51", "https://example.test/NFE/reused.pdf"),
                "Nota Técnica 2025.002\nVersão 1.52\nSumário\nHistórico v1.51").orElseThrow();
        assertEquals(new TechnicalNoteIdentity("NFE_NFCE", 2025, 2, "1.52"), key);
    }

    @Test void portalIdentifiesNfeWithoutInferringFamilyFromGenericSvrsSource() {
        assertTrue(TechnicalNoteIdentity.resolve(request("SVRS", "NT 2025.002 v1.51", "https://example.test/generic.pdf"), "texto").isEmpty());
        assertTrue(TechnicalNoteIdentity.resolve(request("PORTAL_NFE", "NT 2025.002 v1.51", "https://example.test/download.aspx"), "texto").isPresent());
    }

    @Test void rejectsConflictingNumbersAmbiguousVersionsAndOtherFamilies() {
        var request = request("SVRS", "NT 2025.002 v1.51", "https://example.test/NFE/doc.pdf");
        for (String text : new String[]{"NT 2025.003 v1.51", "NT 2025.002 v1.51 v1.52"})
            assertTrue(TechnicalNoteIdentity.resolve(request, text).isEmpty());
        assertTrue(TechnicalNoteIdentity.resolve(request("SVRS", "CT-e NT 2025.002 v1.51", "https://example.test/CTE/a.pdf"), "texto").isEmpty());
        assertTrue(TechnicalNoteIdentity.resolve(request("PORTAL_NFE", "NT 2025.000 v1.51", "https://example.test/a.pdf"), "texto").isEmpty());
    }
}
