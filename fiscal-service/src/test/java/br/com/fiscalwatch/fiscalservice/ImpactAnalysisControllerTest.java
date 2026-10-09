package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.impactanalysis.controller.ImpactAnalysisController;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.SchemaComparisonRequest;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus;
import br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel;
import br.com.fiscalwatch.fiscalservice.impactanalysis.exception.ImpactAnalysisNotFoundException;
import br.com.fiscalwatch.fiscalservice.impactanalysis.exception.InvalidPublicationDocumentException;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.ImpactAnalysisService;
import br.com.fiscalwatch.fiscalservice.publication.exception.GlobalExceptionHandler;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImpactAnalysisControllerTest {

    @Mock
    private ImpactAnalysisService impactAnalysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        ImpactAnalysisController controller =
                new ImpactAnalysisController(impactAnalysisService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deveAnalisarPublicacaoERetornarCreated() throws Exception {

        Long publicationId = 10L;
        ImpactAnalysisResponse response = criarResponse(1L, publicationId);

        when(impactAnalysisService.analyzePublication(publicationId))
                .thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/impact-analyses/publications/{publicationId}/analyze",
                                publicationId
                        )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.publicationId").value(publicationId))
                .andExpect(jsonPath("$.summary").value(response.summary()))
                .andExpect(jsonPath("$.impactLevel").value("HIGH"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(impactAnalysisService).analyzePublication(publicationId);
    }

    @Test
    void deveRetornar422QuandoDocumentoNaoPermitirAnalise() throws Exception {
        when(impactAnalysisService.analyzePublication(10L))
                .thenThrow(new InvalidPublicationDocumentException());
        mockMvc.perform(post("/api/impact-analyses/publications/10/analyze"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void deveAnalisarComparacaoDeSchemaERetornarCreated() throws Exception {

        ImpactAnalysisResponse response = criarResponse(1L, 10L);

        when(impactAnalysisService.analyzeSchemaComparison(
                any(SchemaComparisonRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/impact-analyses/schema-comparisons")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "currentExternalId": "external-1",
                                          "previousExternalId": "external-0",
                                          "currentVersion": "2025.002 v1.30",
                                          "previousVersion": "2025.002 v1.20",
                                          "changes": [
                                            {
                                              "artifact": "DFeTiposBasicos_v1.00.xsd",
                                              "changeType": "TYPE_CHANGED",
                                              "schemaPath": "complexType:TCIBS/element:vBC",
                                              "symbolName": "vBC",
                                              "before": "TDec1302",
                                              "after": "TDec1302RTC"
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.publicationId").value(10L))
                .andExpect(jsonPath("$.impactLevel").value("HIGH"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(impactAnalysisService).analyzeSchemaComparison(
                any(SchemaComparisonRequest.class)
        );
    }

    @Test
    void deveBuscarAnalisePorIdERetornarOk() throws Exception {

        Long id = 1L;
        ImpactAnalysisResponse response = criarResponse(id, 10L);

        when(impactAnalysisService.findById(id))
                .thenReturn(response);

        mockMvc.perform(get("/api/impact-analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.publicationId").value(10L))
                .andExpect(jsonPath("$.summary").value(response.summary()));

        verify(impactAnalysisService).findById(id);
    }

    @Test
    void deveBuscarAnalisesPorPublicationIdERetornarOk() throws Exception {

        Long publicationId = 10L;
        ImpactAnalysisResponse first = criarResponse(1L, publicationId);
        ImpactAnalysisResponse second = criarResponse(2L, publicationId);

        when(impactAnalysisService.findByPublicationId(publicationId))
                .thenReturn(List.of(first, second));

        mockMvc.perform(
                        get(
                                "/api/impact-analyses/publications/{publicationId}",
                                publicationId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].publicationId").value(publicationId))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].publicationId").value(publicationId));

        verify(impactAnalysisService).findByPublicationId(publicationId);
    }

    @Test
    void deveRetornarNotFoundQuandoPublicacaoNaoExistir() throws Exception {

        Long publicationId = 10L;

        when(impactAnalysisService.analyzePublication(publicationId))
                .thenThrow(new PublicationNotFoundException(publicationId));

        mockMvc.perform(
                        post(
                                "/api/impact-analyses/publications/{publicationId}/analyze",
                                publicationId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Publicação não encontrada com id:" + publicationId));

        verify(impactAnalysisService).analyzePublication(publicationId);
    }

    @Test
    void deveRetornarNotFoundQuandoAnaliseNaoExistir() throws Exception {

        Long id = 1L;

        when(impactAnalysisService.findById(id))
                .thenThrow(new ImpactAnalysisNotFoundException(id));

        mockMvc.perform(get("/api/impact-analyses/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Análise de impacto não encontrada com id: " + id));

        verify(impactAnalysisService).findById(id);
    }

    private ImpactAnalysisResponse criarResponse(Long id, Long publicationId) {

        return new ImpactAnalysisResponse(
                id,
                publicationId,
                "Analise automatica da publicacao",
                ImpactLevel.HIGH,
                AnalysisStatus.COMPLETED,
                "automatic-v1",
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 9, 28, 12, 0),
                List.of(),
                List.of(),
                List.of(),
                LocalDateTime.of(2026, 9, 28, 12, 0),
                LocalDateTime.of(2026, 9, 28, 12, 0)
        );
    }
}
