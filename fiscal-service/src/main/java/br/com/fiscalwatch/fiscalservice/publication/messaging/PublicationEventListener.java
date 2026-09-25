package br.com.fiscalwatch.fiscalservice.publication.messaging;

import br.com.fiscalwatch.fiscalservice.publication.exception.PublicationAlreadyExistsException;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PublicationEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(PublicationEventListener.class);

    private final PublicationService publicationService;


    @RabbitListener(queues = RabbitMqConstants.PUBLICATION_DISCOVERED_QUEUE )
    public void consume(PublicationEvent event) {

        log.info(
                "Evento recebido. tipo={}, externalId={}, fonte={}",
                event.eventType(),
                event.publication().externalId(),
                event.publication().source()
        );

        try {
            publicationService.create(event.publication());

            log.info(
                    "Publicação processada com sucesso. externalId={}",
                    event.publication().externalId()
            );

        } catch (PublicationAlreadyExistsException exception) {

            log.info(
                    "Publicação já processada. Evento ignorado. externalId={}",
                    event.publication().externalId()
            );
        }
    }
}