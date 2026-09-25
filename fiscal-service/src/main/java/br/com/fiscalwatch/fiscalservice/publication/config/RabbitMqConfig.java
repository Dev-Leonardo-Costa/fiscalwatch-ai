package br.com.fiscalwatch.fiscalservice.publication.config;

import br.com.fiscalwatch.fiscalservice.publication.messaging.RabbitMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter rabbitMessageConverter() {

        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();

        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public TopicExchange publicationExchange() {
        return new TopicExchange(
                RabbitMqConstants.PUBLICATION_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public Queue publicationDiscoveredQueue() {
        return QueueBuilder
                .durable(RabbitMqConstants.PUBLICATION_DISCOVERED_QUEUE)
                .deadLetterExchange(RabbitMqConstants.PUBLICATION_EXCHANGE)
                .deadLetterRoutingKey(
                        RabbitMqConstants.PUBLICATION_DLQ_ROUTING_KEY
                )
                .build();
    }

    @Bean
    public Queue publicationDlq() {
        return QueueBuilder
                .durable(RabbitMqConstants.PUBLICATION_DLQ)
                .build();
    }

    @Bean
    public Binding publicationDiscoveredBinding() {
        return BindingBuilder
                .bind(publicationDiscoveredQueue())
                .to(publicationExchange())
                .with(
                        RabbitMqConstants.PUBLICATION_DISCOVERED_ROUTING_KEY
                );
    }

    @Bean
    public Binding publicationDlqBinding() {
        return BindingBuilder
                .bind(publicationDlq())
                .to(publicationExchange())
                .with(
                        RabbitMqConstants.PUBLICATION_DLQ_ROUTING_KEY
                );
    }
}