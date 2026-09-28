package br.com.fiscalwatch.fiscalservice.impactanalysis.controller;

import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.service.ImpactAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/impact-analyses")
public class ImpactAnalysisController {

    private final ImpactAnalysisService impactAnalysisService;

    public ImpactAnalysisController(
            ImpactAnalysisService impactAnalysisService
    ) {
        this.impactAnalysisService = impactAnalysisService;
    }

    @PostMapping("/publications/{publicationId}/analyze")
    public ResponseEntity<ImpactAnalysisResponse> analyzePublication(
            @PathVariable Long publicationId
    ) {

        ImpactAnalysisResponse response =
                impactAnalysisService.analyzePublication(publicationId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImpactAnalysisResponse> findById(
            @PathVariable Long id
    ) {

        ImpactAnalysisResponse response = impactAnalysisService.findById(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/publications/{publicationId}")
    public ResponseEntity<List<ImpactAnalysisResponse>> findByPublicationId(
            @PathVariable Long publicationId
    ) {

        List<ImpactAnalysisResponse> response =
                impactAnalysisService.findByPublicationId(publicationId);

        return ResponseEntity.ok(response);
    }
}
