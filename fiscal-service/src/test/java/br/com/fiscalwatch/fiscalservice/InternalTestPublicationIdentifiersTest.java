package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.service.InternalTestPublicationIdentifiers;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InternalTestPublicationIdentifiersTest {
    @Test
    void recognizesOnlyReservedIdentifiersAndExactSource() {
        assertTrue(InternalTestPublicationIdentifiers.matches("INTEGRATION_TEST", "hash", null));
        for (String suffix : new String[]{"valid", "failed"}) {
            assertTrue(InternalTestPublicationIdentifiers.matches("SVRS", "it-svrs-job-" + "a".repeat(32) + "-" + suffix, null));
        }
        assertTrue(InternalTestPublicationIdentifiers.matches("SVRS", "a".repeat(64),
                "https://fixtures.invalid/svrs/mvp-" + "b".repeat(32) + ".pdf"));
        assertFalse(InternalTestPublicationIdentifiers.matches("SVRS", "a".repeat(64), "https://dfe-portal.svrs.rs.gov.br/NFE/nt.pdf"));
        assertFalse(InternalTestPublicationIdentifiers.matches("SVRS_TEST", "a".repeat(64), "https://example.com/teste.pdf"));
        assertFalse(InternalTestPublicationIdentifiers.matches("SVRS", "it-svrs-job-not-a-uuid-valid", null));
        assertFalse(InternalTestPublicationIdentifiers.matches("SVRS", "hash", "https://fixtures.invalid.evil/svrs/mvp-" + "b".repeat(32) + ".pdf"));
    }
}
