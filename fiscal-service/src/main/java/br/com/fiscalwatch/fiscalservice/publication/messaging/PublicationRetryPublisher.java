package br.com.fiscalwatch.fiscalservice.publication.messaging;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Aguarda ACK correlacionado e verifica returns com mandatory habilitado.
 * Isso não confirma processamento pelo consumidor nem torna a republicação
 * atômica com o ACK da mensagem original. Falhas e timeout devem chegar ao
 * chamador; timeout tem resultado incerto e uma nova publicação pode duplicar.
 */
@Component
public class PublicationRetryPublisher {

    public static final String RETRY_COUNT_HEADER =
            "x-fiscalwatch-retry-count";
    public static final String ERROR_TYPE_HEADER =
            "x-fiscalwatch-error-type";
    public static final String ERROR_MESSAGE_HEADER =
            "x-fiscalwatch-error-message";
    public static final String ERROR_AT_HEADER =
            "x-fiscalwatch-error-at";

    private static final long PUBLISH_CONFIRM_TIMEOUT_MS = 5_000;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 300;

    private final RabbitOperations rabbitOperations;

    public PublicationRetryPublisher(RabbitOperations rabbitOperations) {
        this.rabbitOperations = rabbitOperations;
    }

    public void publish(PublicationEvent event) {
        publishToRetry(event);
    }

    public void publishToRetry(PublicationEvent event) {
        publishToRetry(event, Map.of());
    }

    public void publishToRetry(
            PublicationEvent event,
            Map<String, Object> originalHeaders
    ) {

        int count = retryCount(originalHeaders);
        if (count == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Contador de retry esgotado");
        }
        int nextRetryCount = count + 1;

        publish(
                event,
                RabbitMqConstants.PUBLICATION_RETRY_ROUTING_KEY,
                message -> withHeaders(
                        message,
                        originalHeaders,
                        Map.of(RETRY_COUNT_HEADER, nextRetryCount)
                )
        );
    }

    public void publishToDlq(
            PublicationEvent event,
            Throwable exception
    ) {
        publishToDlq(event, Map.of(), exception);
    }

    public void publishToDlq(
            PublicationEvent event,
            Map<String, Object> originalHeaders,
            Throwable exception
    ) {

        Map<String, Object> dlqHeaders = new HashMap<>();
        dlqHeaders.put(RETRY_COUNT_HEADER, retryCount(originalHeaders));
        dlqHeaders.put(ERROR_TYPE_HEADER, exception.getClass().getName());
        dlqHeaders.put(ERROR_MESSAGE_HEADER, sanitizedMessage(exception));
        dlqHeaders.put(ERROR_AT_HEADER, Instant.now().toString());

        publish(
                event,
                RabbitMqConstants.PUBLICATION_DLQ_ROUTING_KEY,
                message -> withHeaders(message, originalHeaders, dlqHeaders)
        );
    }

    private void publish(
            PublicationEvent event,
            String routingKey,
            MessagePostProcessor postProcessor
    ) {

        CorrelationData correlationData = new CorrelationData(
                UUID.randomUUID().toString()
        );

        rabbitOperations.convertAndSend(
                RabbitMqConstants.PUBLICATION_EXCHANGE,
                routingKey,
                event,
                postProcessor,
                correlationData
        );
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(PUBLISH_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            // Spring AMQP preenche o return antes de completar o future.
            if (correlationData.getReturned() != null) {
                throw new AmqpException("Publicação retornada sem roteamento: "
                        + routingKey + " ("
                        + correlationData.getReturned().getReplyText() + ")");
            }
            if (!confirm.ack()) {
                throw new AmqpException("NACK ao publicar em " + routingKey
                        + ": " + confirm.reason());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Espera de confirmação interrompida", exception);
        } catch (TimeoutException exception) {
            throw new AmqpException("Timeout de confirmação em " + routingKey, exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("Falha de confirmação em " + routingKey,
                    exception.getCause());
        }
    }

    private Message withHeaders(
            Message message,
            Map<String, Object> originalHeaders,
            Map<String, Object> overrideHeaders
    ) {

        Map<String, Object> headers =
                message.getMessageProperties().getHeaders();

        headers.putAll(originalHeaders);
        headers.putAll(overrideHeaders);

        return message;
    }

    /**
     * Ausência significa zero. Somente inteiros não negativos até MAX_VALUE
     * são aceitos (tipos integrais AMQP ou string decimal). Valores inválidos
     * são rejeitados antes do envio, sem reiniciar o orçamento de tentativas.
     * MAX_VALUE pode ir à DLQ, mas não pode ser incrementado para retry.
     */
    public static int retryCount(Map<String, Object> headers) {

        Object value = headers.get(RETRY_COUNT_HEADER);

        if (!headers.containsKey(RETRY_COUNT_HEADER)) {
            return 0;
        }
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger || value instanceof String) {
            String text = value.toString();
            if (text.matches("[0-9]+")) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Contador de retry fora do limite", exception);
                }
            }
        }
        throw new IllegalArgumentException("Contador de retry inválido: " + value);
    }

    private String sanitizedMessage(Throwable exception) {

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        String sanitized = message
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();

        if (sanitized.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return sanitized;
        }

        return sanitized.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
