package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChange;
import br.com.fiscalwatch.fiscalservice.fiscalchange.FiscalChangeType;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.EvidenceResult;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.DocumentContext;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules.FiscalAnalysisContext;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiscalAnalysisContextTest {

    @Test
    void devePreservarDocumentContextEFiscalChanges() {
        DocumentContext documentContext = documentContext();
        FiscalChange fiscalChange = fiscalChange("I08-140");

        FiscalAnalysisContext context = new FiscalAnalysisContext(
                documentContext,
                List.of(fiscalChange)
        );

        assertSame(documentContext, context.documentContext());
        assertEquals(List.of(fiscalChange), context.fiscalChanges());
    }

    @Test
    void deveNormalizarFiscalChangesNullParaListaVazia() {
        FiscalAnalysisContext context = new FiscalAnalysisContext(
                documentContext(),
                null
        );

        assertTrue(context.fiscalChanges().isEmpty());
    }

    @Test
    void naoDevePermitirMutabilidadeAcidentalDaListaExterna() {
        FiscalChange firstChange = fiscalChange("I08-140");
        FiscalChange secondChange = fiscalChange("X01-999");
        List<FiscalChange> externalChanges = new ArrayList<>();
        externalChanges.add(firstChange);

        FiscalAnalysisContext context = new FiscalAnalysisContext(
                documentContext(),
                externalChanges
        );

        externalChanges.add(secondChange);

        assertEquals(List.of(firstChange), context.fiscalChanges());
        assertThrows(
                UnsupportedOperationException.class,
                () -> context.fiscalChanges().add(secondChange)
        );
    }

    private DocumentContext documentContext() {
        return new DocumentContext(
                "Nota Técnica",
                null,
                DocumentType.NOTA_TECNICA,
                "https://example.com/publicacao.pdf",
                "Texto oficial.",
                "https://example.com/documento.pdf",
                true
        );
    }

    private FiscalChange fiscalChange(String ruleCode) {
        return new FiscalChange(
                FiscalChangeType.VALIDATION_RULE_CHANGE,
                ruleCode,
                "CFOP",
                "NF-e modelo 55",
                List.of("1.949", "2.949"),
                "Alteração da regra de validação.",
                new EvidenceResult(
                        "Nota Técnica",
                        "https://example.com/documento.pdf",
                        null,
                        null,
                        "Trecho oficial.",
                        0,
                        16
                )
        );
    }
}
