package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChange;
import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChangeType;
import br.com.fiscalwatch.fiscalservice.fiscalchange.RuleBasedFiscalChangeDetector;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.DocumentContext;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfSystemProperty(
        named = "fiscalwatch.real-publication-test",
        matches = "true"
)
class RealPublicationFiscalChangeDetectorIntegrationTest {

    private static final Long REAL_PUBLICATION_ID = 38L;

    private final RuleBasedFiscalChangeDetector detector =
            new RuleBasedFiscalChangeDetector();

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private PublicationDocumentRepository publicationDocumentRepository;

    @Test
    void deveDetectarFiscalChangeNoDocumentoRealDaNt2026009() {
        PublicationEntity publication = publicationRepository
                .findById(REAL_PUBLICATION_ID)
                .orElseThrow();
        PublicationDocumentEntity document = publicationDocumentRepository
                .findByPublicationId(REAL_PUBLICATION_ID)
                .orElseThrow();

        DocumentContext context = new DocumentContext(
                publication.getTitle(),
                publication.getDescription(),
                publication.getDocumentType(),
                publication.getDownloadUrl(),
                document.getContentText(),
                document.getSourceUrl(),
                true
        );

        List<FiscalChange> changes = detector.detect(context);

        assertEquals(1, changes.size());

        FiscalChange change = changes.getFirst();

        assertEquals(
                FiscalChangeType.VALIDATION_RULE_CHANGE,
                change.changeType()
        );
        assertEquals("I08-140", change.ruleCode());
        assertEquals("CFOP", change.affectedElement());
        assertEquals("NF-e modelo 55", change.affectedDocument());
        assertEquals(List.of("1.949", "2.949"), change.referencedValues());
        assertNotNull(change.description());
        assertFalse(change.description().isBlank());

        var evidence = change.evidence();

        assertNotNull(evidence);
        assertEquals(publication.getTitle(), evidence.sourceTitle());
        assertEquals(document.getSourceUrl(), evidence.sourceUrl());
        assertNotNull(evidence.excerpt());
        assertFalse(evidence.excerpt().isBlank());
        assertTrue(document.getContentText().contains(evidence.excerpt()));
        assertTrue(evidence.startPosition() >= 0);
        assertTrue(evidence.endPosition() > evidence.startPosition());
        assertTrue(evidence.endPosition() <= document.getContentText().length());
        assertEquals(
                document.getContentText().substring(
                        evidence.startPosition(),
                        evidence.endPosition()
                ),
                evidence.excerpt()
        );
        assertTrue(evidence.excerpt().contains(
                "Esta Nota Técnica altera a regra de validação I08-140"
        ));
        assertTrue(evidence.excerpt().contains("NF-e"));
        assertTrue(evidence.excerpt().contains("modelo 55"));
        assertTrue(evidence.excerpt().contains("CFOP 1.949 e 2.949"));
        assertFalse(evidence.excerpt().startsWith("1.00 09/2026"));
    }
}
