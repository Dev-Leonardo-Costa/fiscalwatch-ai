package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.controller.PublicationController;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationDocumentHistoryResponse;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationHistoryResponse;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.exception.GlobalExceptionHandler;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicationControllerTest {

    @Mock
    private PublicationService publicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        PublicationController controller =
                new PublicationController(publicationService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deveListarHistoricoDePublicacoesPorSourceEDocumentType()
            throws Exception {

        PublicationHistoryResponse response = criarHistorico();

        when(publicationService.findHistory("SVRS", DocumentType.SCHEMA))
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/publications/history")
                                .param("source", "SVRS")
                                .param("documentType", "SCHEMA")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].externalId").value("a".repeat(64)))
                .andExpect(jsonPath("$[0].source").value("SVRS"))
                .andExpect(jsonPath("$[0].title").value("Pacote de schemas"))
                .andExpect(jsonPath("$[0].documentType").value("SCHEMA"))
                .andExpect(jsonPath("$[0].downloadUrl")
                        .value("https://example.com/schema.zip"))
                .andExpect(jsonPath("$[0].document.contentText")
                        .value("=== arquivo: schema.xsd ===\n<xs:schema/>"))
                .andExpect(jsonPath("$[0].document.contentHash")
                        .value("b".repeat(64)))
                .andExpect(jsonPath("$[0].document.contentLength").value(38))
                .andExpect(jsonPath("$[0].document.extractionStatus")
                        .value("EXTRACTED"))
                .andExpect(jsonPath("$[0].document.extractorVersion")
                        .value("svrs-schema-zip-v1"));

        verify(publicationService).findHistory("SVRS", DocumentType.SCHEMA);
    }

    private PublicationHistoryResponse criarHistorico() {
        LocalDateTime extractedAt = LocalDateTime.of(2026, 1, 2, 10, 0);

        return new PublicationHistoryResponse(
                10L,
                "a".repeat(64),
                "SVRS",
                "Pacote de schemas",
                DocumentType.SCHEMA,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                "https://example.com/schema.zip",
                new PublicationDocumentHistoryResponse(
                        "=== arquivo: schema.xsd ===\n<xs:schema/>",
                        "b".repeat(64),
                        38,
                        ExtractionStatus.EXTRACTED,
                        "svrs-schema-zip-v1",
                        extractedAt
                )
        );
    }
}
