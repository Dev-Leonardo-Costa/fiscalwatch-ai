package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEvent;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationRetryPublisher;
import br.com.fiscalwatch.fiscalservice.publication.messaging.RabbitMqConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitOperations;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class PublicationRetryPublisherTest {

    @Mock
    private RabbitOperations rabbitOperations;

    private PublicationRetryPublisher publisher;

    @BeforeEach
    void configurar() {
        publisher = new PublicationRetryPublisher(rabbitOperations);

    }

    @Test
    void devePublicarRetryNaRoutingKeyDeRetryComContadorInicial()
            throws Exception {

        PublicationEvent event = criarEvento();

        confirmarEnvio();

        publisher.publishToRetry(event);

        MessagePostProcessor postProcessor = capturarPublicacao(
                event,
                RabbitMqConstants.PUBLICATION_RETRY_ROUTING_KEY
        );
        Message message = aplicar(postProcessor);

        assertEquals(
                1,
                message.getMessageProperties().getHeaders().get(
                        PublicationRetryPublisher.RETRY_COUNT_HEADER
                )
        );
    }

    @Test
    void deveIncrementarContadorDeRetryPreservandoHeadersOriginais()
            throws Exception {

        PublicationEvent event = criarEvento();
        confirmarEnvio();
        Map<String, Object> headers = Map.of(
                PublicationRetryPublisher.RETRY_COUNT_HEADER,
                2,
                "x-original-source",
                "collector-service"
        );

        publisher.publishToRetry(event, headers);

        MessagePostProcessor postProcessor = capturarPublicacao(
                event,
                RabbitMqConstants.PUBLICATION_RETRY_ROUTING_KEY
        );
        Map<String, Object> resultHeaders = aplicar(postProcessor)
                .getMessageProperties()
                .getHeaders();

        assertEquals(
                3,
                resultHeaders.get(PublicationRetryPublisher.RETRY_COUNT_HEADER)
        );
        assertEquals("collector-service", resultHeaders.get("x-original-source"));
    }

    @Test
    void devePublicarNaDlqComHeadersDeErroSanitizados()
            throws Exception {

        PublicationEvent event = criarEvento();
        confirmarEnvio();
        RuntimeException exception = new RuntimeException(
                "falha controlada\ncom detalhe interno"
        );

        publisher.publishToDlq(
                event,
                Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER, 3,
                        "x-original-source", "collector-service"),
                exception
        );

        MessagePostProcessor postProcessor = capturarPublicacao(
                event,
                RabbitMqConstants.PUBLICATION_DLQ_ROUTING_KEY
        );
        Map<String, Object> headers = aplicar(postProcessor)
                .getMessageProperties()
                .getHeaders();

        assertEquals(3, headers.get(PublicationRetryPublisher.RETRY_COUNT_HEADER));
        assertEquals("collector-service", headers.get("x-original-source"));
        assertEquals(
                RuntimeException.class.getName(),
                headers.get(PublicationRetryPublisher.ERROR_TYPE_HEADER)
        );
        assertEquals(
                "falha controlada com detalhe interno",
                headers.get(PublicationRetryPublisher.ERROR_MESSAGE_HEADER)
        );
        assertTrue(headers.containsKey(PublicationRetryPublisher.ERROR_AT_HEADER));
    }

    private void confirmarEnvio() {
        responderEnvio(cd -> cd.getFuture().complete(new CorrelationData.Confirm(true, null)));
    }

    private void responderEnvio(java.util.function.Consumer<CorrelationData> resposta) {
        doAnswer(invocation -> {
            resposta.accept(invocation.getArgument(4));
            return null;
        }).when(rabbitOperations).convertAndSend(any(String.class), any(String.class),
                any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    private void publicar(boolean dlq, Map<String, Object> headers) {
        if (dlq) {
            publisher.publishToDlq(criarEvento(), headers, new RuntimeException("erro"));
        } else {
            publisher.publishToRetry(criarEvento(), headers);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void devePropagarFalhaDeEnvioParaRetryEDlq(boolean dlq) {
        AmqpException falha = new AmqpException("envio falhou");
        doThrow(falha).when(rabbitOperations).convertAndSend(any(String.class),
                any(String.class), any(Object.class), any(MessagePostProcessor.class),
                any(CorrelationData.class));
        assertSame(falha, assertThrows(AmqpException.class, () -> publicar(dlq, Map.of())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void devePropagarNackParaRetryEDlq(boolean dlq) {
        responderEnvio(cd -> cd.getFuture().complete(new CorrelationData.Confirm(false, "negado")));
        AmqpException falha = assertThrows(AmqpException.class, () -> publicar(dlq, Map.of()));
        assertTrue(falha.getMessage().contains("NACK"));
        assertTrue(falha.getMessage().contains("negado"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void devePropagarTimeoutSemConfirmacaoParaRetryEDlq(boolean dlq) {
        long inicio = System.nanoTime();
        AmqpException falha = assertThrows(AmqpException.class, () -> publicar(dlq, Map.of()));
        assertTrue(falha.getCause() instanceof TimeoutException);
        assertTrue(System.nanoTime() - inicio >= java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveRejeitarReturnMesmoComAckParaRetryEDlq(boolean dlq) {
        responderEnvio(cd -> {
            cd.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                    312, "NO_ROUTE", RabbitMqConstants.PUBLICATION_EXCHANGE, "sem.rota"));
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
        });
        AmqpException falha = assertThrows(AmqpException.class, () -> publicar(dlq, Map.of()));
        assertTrue(falha.getMessage().contains("NO_ROUTE"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void devePropagarFalhaDoFutureParaRetryEDlq(boolean dlq) {
        RuntimeException causa = new RuntimeException("conexao fechada");
        responderEnvio(cd -> cd.getFuture().completeExceptionally(causa));
        assertSame(causa, assertThrows(AmqpException.class,
                () -> publicar(dlq, Map.of())).getCause());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveRestaurarInterrupcaoAoAguardarConfirmacao(boolean dlq) {
        Thread.currentThread().interrupt();
        try {
            AmqpException falha = assertThrows(AmqpException.class, () -> publicar(dlq, Map.of()));
            assertTrue(falha.getCause() instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveRejeitarContadoresInvalidosSemPublicar(boolean dlq) {
        Object[] invalidos = {"", "abc", "-1", -1, -1L, "2147483648",
                2147483648L, new BigInteger("999999999999999999999"),
                1.5, 1.0, true, " 2", "+2", null};
        for (Object invalido : invalidos) {
            Map<String, Object> headers = new HashMap<>();
            headers.put(PublicationRetryPublisher.RETRY_COUNT_HEADER, invalido);
            assertThrows(IllegalArgumentException.class, () -> publicar(dlq, headers));
        }
        verifyNoInteractions(rabbitOperations);
    }

    @Test
    void deveRejeitarOverflowAoIncrementarRetrySemPublicar() {
        assertThrows(IllegalArgumentException.class, () -> publicar(false,
                Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER, Integer.MAX_VALUE)));
        verifyNoInteractions(rabbitOperations);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveAceitarContadorAusenteOuInteiroPreservandoHeaders(boolean dlq) {
        java.util.concurrent.atomic.AtomicInteger contadorEsperado =
                new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            MessagePostProcessor processador = invocation.getArgument(3);
            MessageProperties propriedades = new MessageProperties();
            propriedades.setHeader("header-conversor", "preservado");
            Message mensagem = processador.postProcessMessage(new Message(new byte[0], propriedades));
            Map<String, Object> headers = mensagem.getMessageProperties().getHeaders();
            assertEquals("preservado", headers.get("header-conversor"));
            assertEquals("origem", headers.get("x-original"));
            assertEquals(contadorEsperado.get(), headers.get(PublicationRetryPublisher.RETRY_COUNT_HEADER));
            assertEquals(dlq ? RabbitMqConstants.PUBLICATION_DLQ_ROUTING_KEY
                    : RabbitMqConstants.PUBLICATION_RETRY_ROUTING_KEY, invocation.getArgument(1));
            CorrelationData cd = invocation.getArgument(4);
            assertFalse(cd.getId().isBlank());
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitOperations).convertAndSend(any(String.class), any(String.class),
                any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        Object[] validos = {null, 0, (byte) 2, (short) 2, 2, 2L, "2",
                BigInteger.valueOf(2), Integer.MAX_VALUE - 1};
        for (Object valido : validos) {
            Map<String, Object> headers = new HashMap<>(Map.of("x-original", "origem"));
            if (valido != null) headers.put(PublicationRetryPublisher.RETRY_COUNT_HEADER, valido);
            contadorEsperado.set((valido == null ? 0 : Integer.parseInt(valido.toString()))
                    + (dlq ? 0 : 1));
            publicar(dlq, headers);
            assertEquals(valido, headers.get(PublicationRetryPublisher.RETRY_COUNT_HEADER));
        }
    }

    @Test
    void deveManterContadorMaximoNaDlqELimitarMensagemDeErro() {
        confirmarEnvio();
        PublicationEvent event = criarEvento();
        publisher.publishToDlq(event, Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER,
                Integer.MAX_VALUE), new RuntimeException("a".repeat(400)));
        Map<String, Object> headers = aplicar(capturarPublicacao(event,
                RabbitMqConstants.PUBLICATION_DLQ_ROUTING_KEY)).getMessageProperties().getHeaders();
        assertEquals(Integer.MAX_VALUE, headers.get(PublicationRetryPublisher.RETRY_COUNT_HEADER));
        assertEquals(300, ((String) headers.get(PublicationRetryPublisher.ERROR_MESSAGE_HEADER)).length());
    }

    @Test
    void deveUsarTipoDeErroQuandoMensagemAusente() {
        confirmarEnvio();
        PublicationEvent event = criarEvento();
        publisher.publishToDlq(event, new RuntimeException());
        assertEquals("RuntimeException", aplicar(capturarPublicacao(event,
                RabbitMqConstants.PUBLICATION_DLQ_ROUTING_KEY)).getMessageProperties()
                .getHeader(PublicationRetryPublisher.ERROR_MESSAGE_HEADER));
    }

    private MessagePostProcessor capturarPublicacao(
            PublicationEvent event,
            String routingKey
    ) {

        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);

        verify(rabbitOperations).convertAndSend(
                eq(RabbitMqConstants.PUBLICATION_EXCHANGE),
                eq(routingKey),
                same(event),
                postProcessorCaptor.capture(),
                any(CorrelationData.class)
        );

        return postProcessorCaptor.getValue();
    }

    private Message aplicar(MessagePostProcessor postProcessor) {

        return postProcessor.postProcessMessage(new Message(
                new byte[0],
                new MessageProperties()
        ));
    }

    private PublicationEvent criarEvento() {

        return new PublicationEvent(
                "publication.discovered",
                LocalDateTime.of(2026, 10, 8, 12, 0),
                new PublicationRequest(
                        "a".repeat(64),
                        "SVRS",
                        "Nota Tecnica 2026.009 v1.00",
                        DocumentType.NOTA_TECNICA,
                        LocalDateTime.of(2026, 10, 8, 10, 0),
                        null,
                        "Publicacao para teste",
                        "https://example.com/nota-tecnica.pdf"
                ),
                null
        );
    }
}
