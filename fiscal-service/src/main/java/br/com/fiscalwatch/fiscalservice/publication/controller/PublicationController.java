package br.com.fiscalwatch.fiscalservice.publication.controller;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationHistoryResponse;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationCollectionState;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationResponse;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/publications")
public class PublicationController {

    @GetMapping("/collection-state")
    public ResponseEntity<PublicationCollectionState>
            findCollectionState(@RequestParam String externalId) {
        if (!externalId.matches("[0-9a-f]{64}")) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(publicationService.findSvrsCollectionState(externalId));
    }

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @PostMapping
    public ResponseEntity<PublicationResponse> create(
            @Valid @RequestBody
            PublicationRequest request
    ) {

        PublicationResponse response = publicationService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicationResponse> findById(
            @PathVariable Long id
    ) {

        PublicationResponse response = publicationService.findById(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<PublicationHistoryResponse>> findHistory(
            @RequestParam String source,
            @RequestParam DocumentType documentType
    ) {

        List<PublicationHistoryResponse> response =
                publicationService.findHistory(source, documentType);

        return ResponseEntity.ok(response);
    }
}
