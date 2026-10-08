package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.config.RabbitMqConfig;
import br.com.fiscalwatch.fiscalservice.publication.messaging.RabbitMqConstants;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void deveConfigurarRejeicaoSemRequeueParaEnviarParaDlq()
            throws IOException {

        PropertySource<?> propertySource = applicationProperties();

        assertFalse((Boolean) propertySource.getProperty(
                "spring.rabbitmq.listener.simple.default-requeue-rejected"
        ));
    }

    @Test
    void deveConfigurarConfirmacoesERetornosDePublisher()
            throws IOException {

        PropertySource<?> propertySource = applicationProperties();

        assertEquals(
                "correlated",
                propertySource.getProperty(
                        "spring.rabbitmq.publisher-confirm-type"
                )
        );
        assertEquals(
                true,
                propertySource.getProperty("spring.rabbitmq.publisher-returns")
        );
        assertEquals(
                true,
                propertySource.getProperty("spring.rabbitmq.template.mandatory")
        );
    }

    private PropertySource<?> applicationProperties() throws IOException {

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

        return loader.load(
                "application",
                new ClassPathResource("application.yaml")
        ).getFirst();
    }
}
