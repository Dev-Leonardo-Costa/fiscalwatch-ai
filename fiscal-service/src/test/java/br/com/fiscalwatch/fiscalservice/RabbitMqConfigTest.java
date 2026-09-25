package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.config.RabbitMqConfig;
import br.com.fiscalwatch.fiscalservice.publication.messaging.RabbitMqConstants;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitMqConfigTest {

    private final RabbitMqConfig rabbitMqConfig = new RabbitMqConfig();

    @Test
    void deveConfigurarFilaPrincipalComDeadLetterQueue() {

        Queue queue = rabbitMqConfig.publicationDiscoveredQueue();

        Map<String, Object> arguments = queue.getArguments();

        assertEquals(
                RabbitMqConstants.PUBLICATION_EXCHANGE,
                arguments.get("x-dead-letter-exchange")
        );

        assertEquals(
                RabbitMqConstants.PUBLICATION_DLQ_ROUTING_KEY,
                arguments.get("x-dead-letter-routing-key")
        );
    }

    @Test
    void deveCriarFilaDeDeadLetterComNomeCorreto() {

        Queue queue = rabbitMqConfig.publicationDlq();

        assertEquals(
                RabbitMqConstants.PUBLICATION_DLQ,
                queue.getName()
        );
    }
}