package br.com.fiscalwatch.fiscalservice.publication.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublicationRetryPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PublicationRetryPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(PublicationEvent event) {

        rabbitTemplate.convertAndSend(
                RabbitMqConstants.PUBLICATION_EXCHANGE,
                RabbitMqConstants.PUBLICATION_RETRY_ROUTING_KEY,
                event
        );
    }
}