package br.com.fiscalwatch.fiscalservice.publication.messaging;

import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.stereotype.Component;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLTransientException;
import java.sql.SQLRecoverableException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class PublicationEventListener {

    public static final int MAX_RETRIES = 3;
    public static final String INVALID_RETRY_COUNT_HEADER = "x-fiscalwatch-invalid-retry-count";
    public static final String ORIGINAL_ERROR_TYPE_HEADER = "x-fiscalwatch-original-error-type";
    private static final Set<String> USEFUL_HEADERS = Set.of(
            PublicationRetryPublisher.RETRY_COUNT_HEADER,
            INVALID_RETRY_COUNT_HEADER,
            ORIGINAL_ERROR_TYPE_HEADER,
            PublicationRetryPublisher.ERROR_AT_HEADER,
            "x-original-source", "x-correlation-id", "x-request-id",
            "correlationId", "traceparent", "tracestate", "b3",
            "X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled", "x-death",
            "x-first-death-exchange", "x-first-death-queue", "x-first-death-reason",
            "x-last-death-exchange", "x-last-death-queue", "x-last-death-reason"
    );

    private static final Logger log =
            LoggerFactory.getLogger(PublicationEventListener.class);

    private final PublicationService publicationService;
    private final PublicationRetryPublisher retryPublisher;


    @RabbitListener(queues = RabbitMqConstants.PUBLICATION_DISCOVERED_QUEUE )
    public void consume(PublicationEvent event, @Headers Map<String, Object> originalHeaders) {
        Map<String, Object> headers = usefulHeaders(originalHeaders);
        int retryCount;
        try {
            retryCount = PublicationRetryPublisher.retryCount(headers);
        } catch (IllegalArgumentException exception) {
            // Não reinicia tentativas nem republica o valor inválido/sensível.
            headers.put(PublicationRetryPublisher.RETRY_COUNT_HEADER, MAX_RETRIES);
            headers.put(INVALID_RETRY_COUNT_HEADER, true);
            republish(event, headers, exception, false, "Contador de retry inválido");
            return;
        }

        try {
            if (event == null || event.publication() == null) {
                throw new IllegalArgumentException("Evento sem publicação");
            }

            log.info(
                    "Evento recebido. tipo={}, externalId={}, fonte={}",
                    event.eventType(),
                    event.publication().externalId(),
                    event.publication().source()
            );

            publicationService.processEvent(event);
        } catch (RuntimeException exception) {
            FailureKind kind = classify(exception);
            boolean retry = kind != FailureKind.PERMANENT && retryCount < MAX_RETRIES;
            republish(event, headers, exception, retry,
                    retry ? "Falha de processamento; nova tentativa"
                            : "Falha de processamento; encaminhada à DLQ");
            return;
        }

        log.info(
                "Publicação processada com sucesso. externalId={}",
                event.publication().externalId()
        );
    }

    /**
     * Só retorna após a republicação confirmada sem return. O ACK original e
     * esse envio não são atômicos; uma redelivery pode repetir o processamento.
     * Falha de envio mantém a original por requeue explícito, sem depender da
     * transferência não confirmada pelo dead-letter exchange do broker.
     */
    private void republish(PublicationEvent event, Map<String, Object> headers,
                           RuntimeException failure, boolean retry, String safeMessage) {
        headers.put(ORIGINAL_ERROR_TYPE_HEADER, failure.getClass().getName());
        headers.put(PublicationRetryPublisher.ERROR_TYPE_HEADER, failure.getClass().getName());
        headers.put(PublicationRetryPublisher.ERROR_MESSAGE_HEADER, safeMessage);
        headers.put(PublicationRetryPublisher.ERROR_AT_HEADER, Instant.now().toString());
        try {
            if (retry) {
                retryPublisher.publishToRetry(event, headers);
            } else {
                // Não envia SQL, parâmetros ou valores de validação da exceção.
                retryPublisher.publishToDlq(event, headers,
                        new RuntimeException(safeMessage));
            }
        } catch (RuntimeException publishFailure) {
            throw new ImmediateRequeueAmqpException(
                    "Republicação falhou; manter mensagem original na fila", publishFailure);
        }
        log.warn("Evento encaminhado para {}. tipoErro={}",
                retry ? "retry" : "DLQ", failure.getClass().getName());
    }

    private Map<String, Object> usefulHeaders(Map<String, Object> originalHeaders) {
        Map<String, Object> headers = new HashMap<>();
        originalHeaders.forEach((key, value) -> {
            if (USEFUL_HEADERS.contains(key)) headers.put(key, value);
        });
        return headers;
    }

    private enum FailureKind { TRANSIENT, PERMANENT, UNKNOWN }

    /** Infraestrutura conhecida tem precedência sobre wrappers de validação. */
    private FailureKind classify(Throwable exception) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean permanent = false;
        for (Throwable cause = exception; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof RecoverableDataAccessException
                    || cause instanceof CannotCreateTransactionException
                    || cause instanceof TransactionTimedOutException
                    || cause instanceof SQLTransientException
                    || cause instanceof SQLRecoverableException
                    || cause instanceof AmqpConnectException
                    || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException) {
                return FailureKind.TRANSIENT;
            }
            permanent |= cause instanceof IllegalArgumentException
                    || cause instanceof ConstraintViolationException
                    || cause instanceof DataIntegrityViolationException
                    || cause instanceof org.springframework.amqp.support.converter.MessageConversionException
                    || cause instanceof org.springframework.messaging.converter.MessageConversionException
                    || cause instanceof org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
        }
        return permanent ? FailureKind.PERMANENT : FailureKind.UNKNOWN;
    }
}
