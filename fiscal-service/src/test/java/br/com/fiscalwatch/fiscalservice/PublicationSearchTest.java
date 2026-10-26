package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.entity.PublicationEntity;
import br.com.fiscalwatch.fiscalservice.publication.mapper.PublicationMapper;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationRepository;
import br.com.fiscalwatch.fiscalservice.publication.repository.PublicationDocumentRepository;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:publication-search;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PublicationSearchTest {
    @Autowired PublicationRepository repository;
    @Autowired PublicationDocumentRepository documents;
    @Autowired br.com.fiscalwatch.fiscalservice.impactanalysis.repository.ImpactAnalysisRepository analyses;

    private PublicationService service() {
        return new PublicationService(repository, documents, org.mapstruct.factory.Mappers.getMapper(PublicationMapper.class), null, null);
    }

    @Test
    void searchesNtNumberYearTitleAndDescriptionCaseInsensitively() {
        var publication = save("NT 2021.003 – Validação do GTIN", "Cadastro centralizado", 2023);
        for (String term : new String[]{"2021.003", "2021", "2023", "gtin", "VALIDAÇÃO", "centralizado", "  GTIN  "}) {
            var result = service().search(term);
            assertEquals(1, result.size(), term);
            assertEquals(publication.getId(), result.getFirst().id());
            assertEquals("SVRS", result.getFirst().source());
        }
    }

    @Test
    void returnsEmptyResultsAndTreatsWildcardCharactersLiterally() {
        save("GTIN", null, 2023);
        var literal = save("Percentual %_!", null, 2023);
        assertEquals(literal.getId(), service().search("%_!").getFirst().id());
        assertTrue(service().search("inexistente").isEmpty());
    }

    @Test
    void sortsByPublicationDateThenIdAndLimitsResults() {
        save("NT antiga", null, 2021);
        for (int i = 0; i < 51; i++) save("NT recente", null, 2026);
        var result = service().search("NT");
        assertEquals(50, result.size());
        assertEquals(2026, result.getFirst().publishedAt().getYear());
        assertTrue(result.getFirst().id() > result.getLast().id());
    }

    @Test
    void validatesQueryBeforeSearching() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service().search(" "));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service().search("x".repeat(201)));
    }

    private PublicationEntity save(String title, String description, int year) {
        var publication = new PublicationEntity();
        publication.setExternalId(UUID.randomUUID().toString());
        publication.setTitle(title);
        publication.setDescription(description);
        publication.setSource("SVRS");
        publication.setPublishedAt(LocalDateTime.of(year, 1, 1, 0, 0));
        return repository.saveAndFlush(publication);
    }

    @Test
    void publicQueriesHideExplicitFixturesButKeepOfficialDocumentsWithTestWords() {
        var official = save("NT 2026.009 – TESTE de homologação e informação sintética", "Cadastro GTIN", 2026);
        var sourceFixture = save("GTIN de teste", "Cadastro GTIN", 2026);
        sourceFixture.setSource("INTEGRATION_TEST");
        repository.saveAndFlush(sourceFixture);
        var jobFixture = save("GTIN", "Cadastro GTIN", 2026);
        jobFixture.setExternalId("it-svrs-job-" + "a".repeat(32) + "-valid");
        repository.saveAndFlush(jobFixture);
        var urlFixture = save("GTIN", "Cadastro GTIN", 2026);
        urlFixture.setDownloadUrl("https://fixtures.invalid/svrs/mvp-" + "b".repeat(32) + ".pdf");
        repository.saveAndFlush(urlFixture);
        var declaredFixture = save("GTIN", "Cadastro GTIN", 2026);
        declaredFixture.setRecordOrigin(br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin.INTERNAL_TEST);
        repository.saveAndFlush(declaredFixture);
        for (String term : new String[]{"2026", "GTIN", "Cadastro", "TESTE", "sintética"}) {
            assertEquals(java.util.List.of(official.getId()), service().search(term).stream().map(p -> p.id()).toList(), term);
        }
        assertEquals(official.getId(), service().findById(official.getId()).id());
        for (var fixture : java.util.List.of(sourceFixture, jobFixture, urlFixture, declaredFixture)) {
            assertEquals(new br.com.fiscalwatch.fiscalservice.publication.dto.PublicationCollectionState(
                    fixture.getExternalId(), false, null, false), service().findSvrsCollectionState(fixture.getExternalId()));
            assertThrows(br.com.fiscalwatch.fiscalservice.publication.exception.PublicationNotFoundException.class,
                    () -> service().findById(fixture.getId()));
            assertTrue(repository.findById(fixture.getId()).isPresent());
        }
    }

    @Test
    void historyAndAnalysisQueriesDoNotExposeInternalDocumentsOrEvidence() throws Exception {
        var official = save("NT oficial GTIN", null, 2026);
        var fixture = save("GTIN", null, 2026);
        fixture.setRecordOrigin(br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin.INTERNAL_TEST);
        repository.saveAndFlush(fixture);
        for (var publication : java.util.List.of(official, fixture)) {
            publication.setDocumentType(br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType.NOTA_TECNICA);
            repository.saveAndFlush(publication);
            var document = new br.com.fiscalwatch.fiscalservice.publication.entity.PublicationDocumentEntity();
            document.setPublication(publication);
            document.setContentText("GTIN");
            document.setExtractionStatus(br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus.EXTRACTED);
            documents.saveAndFlush(document);
            var analysis = new br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis();
            analysis.setPublication(publication);
            analysis.setAnalysisVersion("fixture-analysis");
            analysis.setStatus(br.com.fiscalwatch.fiscalservice.impactanalysis.enums.AnalysisStatus.COMPLETED);
            analysis.setImpactLevel(br.com.fiscalwatch.fiscalservice.impactanalysis.enums.ImpactLevel.LOW);
            analyses.saveAndFlush(analysis);
        }
        assertEquals(java.util.List.of(official.getId()), service().findHistory("SVRS",
                br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType.NOTA_TECNICA).stream().map(p -> p.id()).toList());
        var publicOrigin = br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin.PUBLIC;
        assertEquals(1, analyses.findByPublicationIdAndPublicationRecordOrigin(official.getId(), publicOrigin).size());
        assertTrue(analyses.findByPublicationIdAndPublicationRecordOrigin(fixture.getId(), publicOrigin).isEmpty());
        var hidden = analyses.findByPublicationId(fixture.getId()).getFirst();
        assertTrue(analyses.findByIdAndPublicationRecordOrigin(hidden.getId(), publicOrigin).isEmpty());
        assertFalse(repository.existsByIdAndRecordOrigin(fixture.getId(), publicOrigin));
        var service = new br.com.fiscalwatch.fiscalservice.impactanalysis.service.ImpactAnalysisService(
                analyses, repository, documents, org.mapstruct.factory.Mappers.getMapper(
                    br.com.fiscalwatch.fiscalservice.impactanalysis.mapper.ImpactAnalysisMapper.class),
                org.mockito.Mockito.mock(br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.ImpactAnalyzer.class),
                org.mockito.Mockito.mock(br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.SchemaChangeImpactAnalyzer.class), null, null, null);
        assertThrows(br.com.fiscalwatch.fiscalservice.impactanalysis.exception.ImpactAnalysisNotFoundException.class,
                () -> service.findById(hidden.getId()));
        assertThrows(br.com.fiscalwatch.fiscalservice.publication.exception.PublicationNotFoundException.class,
                () -> service.findByPublicationId(fixture.getId()));
        assertEquals(1, service.findByPublicationId(official.getId()).size());
        assertEquals(official.getId(), service.findById(analyses.findByPublicationId(official.getId()).getFirst().getId()).publicationId());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new br.com.fiscalwatch.fiscalservice.publication.controller.PublicationController(service()),
                new br.com.fiscalwatch.fiscalservice.impactanalysis.controller.ImpactAnalysisController(service))
                .setControllerAdvice(new br.com.fiscalwatch.fiscalservice.publication.exception.GlobalExceptionHandler()).build();
        for (String path : java.util.List.of("/api/publications/" + fixture.getId(),
                "/api/impact-analyses/publications/" + fixture.getId(), "/api/impact-analyses/" + hidden.getId())) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/publications/" + official.getId()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test
    void requestSupportsExplicitInternalOriginWhileOldConstructorRemainsCompatible() {
        var request = new br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest("a".repeat(64), "SVRS",
                "GTIN", null, LocalDateTime.of(2026, 1, 1, 0, 0), null, null, null,
                br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin.INTERNAL_TEST);
        var response = service().create(request);
        assertEquals(br.com.fiscalwatch.fiscalservice.publication.enums.PublicationRecordOrigin.INTERNAL_TEST,
                repository.findById(response.id()).orElseThrow().getRecordOrigin());
        assertThrows(br.com.fiscalwatch.fiscalservice.publication.exception.PublicationNotFoundException.class,
                () -> service().findById(response.id()));
    }
}
