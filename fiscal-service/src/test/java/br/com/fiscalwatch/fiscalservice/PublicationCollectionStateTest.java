package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.controller.PublicationController;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.mapper.PublicationMapper;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import br.com.fiscalwatch.fiscalservice.publication.versioning.PublicationSnapshotHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicationCollectionStateTest {
    private final String externalId = "a".repeat(64);
    private PublicationRepository publications;
    private PublicationDocumentRepository documents;
    private PublicationService service;

    @BeforeEach
    void preparar() {
        publications = mock(PublicationRepository.class);
        documents = mock(PublicationDocumentRepository.class);
        service = new PublicationService(publications, documents, mock(PublicationMapper.class));
    }

    private void existente() {
        var publication = new PublicationEntity();
        publication.setId(10L);
        publication.setSource("SVRS");
        when(publications.findByExternalId(externalId)).thenReturn(Optional.of(publication));
    }

    @Test
    void deveInformarPublicacaoAusenteSemConfundirComDocumentoAusente() {
        assertFalse(service.findSvrsCollectionState(externalId).exists());
        verifyNoInteractions(documents);
        existente();
        var state = service.findSvrsCollectionState(externalId);
        assertTrue(state.exists());
        assertNull(state.extractionStatus());
        assertFalse(state.validDocument());
    }

    @ParameterizedTest
    @EnumSource(ExtractionStatus.class)
    void deveDistinguirEstadosEValidarIntegridade(ExtractionStatus status) {
        existente();
        var document = new PublicationDocumentEntity();
        document.setExtractionStatus(status);
        document.setContentText("Conteúdo oficial válido");
        document.setContentLength(document.getContentText().length());
        document.setContentHash(PublicationSnapshotHasher.calculateContentHash(document.getContentText()));
        when(documents.findByPublicationId(10L)).thenReturn(Optional.of(document));
        var state = service.findSvrsCollectionState(externalId);
        assertEquals(status, state.extractionStatus());
        assertEquals(status == ExtractionStatus.EXTRACTED, state.validDocument());
        document.setContentHash("0".repeat(64));
        assertFalse(service.findSvrsCollectionState(externalId).validDocument());
    }

    @Test
    void endpointDeveRetornarEstadoSemConteudoNemFiltrarSchema() throws Exception {
        existente();
        var mvc = MockMvcBuilders.standaloneSetup(new PublicationController(service)).build();
        mvc.perform(get("/api/publications/collection-state").param("externalId", externalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value(externalId))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.validDocument").value(false))
                .andExpect(jsonPath("$.contentText").doesNotExist());
        mvc.perform(get("/api/publications/collection-state").param("externalId", "inválido"))
                .andExpect(status().isBadRequest());
    }
}
