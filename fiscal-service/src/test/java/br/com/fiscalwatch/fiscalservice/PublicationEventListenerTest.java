package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationAlreadyExistsException;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEvent;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEventListener;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublicationEventListenerTest {

    @Mock
    private PublicationService publicationService;

    private PublicationEventListener listener;

    @BeforeEach
    void configurar() {
        listener = new PublicationEventListener(publicationService);
    }

    @Test
    void deveProcessarPublicacaoNova() {

        PublicationRequest publication = criarPublicacao();

        PublicationEvent event = new PublicationEvent(
                "publication.discovered",
                LocalDateTime.now(),
                publication
        );

        listener.consume(event);

        verify(publicationService).create(publication);
    }

    @Test
    void deveIgnorarPublicacaoJaProcessada() {

        PublicationRequest publication = criarPublicacao();

        PublicationEvent event = new PublicationEvent(
                "publication.discovered",
                LocalDateTime.now(),
                publication
        );

        doThrow(new PublicationAlreadyExistsException(publication.externalId()))
                .when(publicationService)
                .create(publication);

        assertDoesNotThrow(() -> listener.consume(event));

        verify(publicationService).create(publication);
    }

    private PublicationRequest criarPublicacao() {
        return new PublicationRequest(
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "SVRS",
                "Nota Técnica 2026.009 v1.00",
                DocumentType.NOTA_TECNICA,
                LocalDateTime.now(),
                null,
                "Publicação utilizada no teste do consumidor",
                null
        );
    }
}