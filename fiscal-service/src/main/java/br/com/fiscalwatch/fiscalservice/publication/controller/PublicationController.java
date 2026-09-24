package br.com.fiscalwatch.fiscalservice.publication.controller;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationResponse;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/publications")
public class PublicationController {

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
}