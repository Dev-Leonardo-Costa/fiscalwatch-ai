package br.com.fiscalwatch.fiscalservice;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEvent;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationEventListener;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationRetryPublisher;
import br.com.fiscalwatch.fiscalservice.publication.service.PublicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.listener.support.ContainerUtils;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import jakarta.validation.ConstraintViolationException;
import org.apache.commons.logging.LogFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.stream.Stream;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PublicationEventListenerTest {

    @Mock
    private PublicationService publicationService;

    @Mock
    private PublicationRetryPublisher retryPublisher;

    private PublicationEventListener listener;

    @BeforeEach
    void configurar() {
        listener = new PublicationEventListener(publicationService, retryPublisher);
    }

    @Test
    void deveProcessarPublicacaoNova() {

        PublicationRequest publication = criarPublicacao();

        PublicationEvent event = new PublicationEvent(
                "publication.discovered",
                LocalDateTime.now(),
                publication,
                null
        );

        listener.consume(event, Map.of());

        verify(publicationService).processEvent(event);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    void deveAceitarEventoAntigoSemDocumento() {

        PublicationRequest publication = criarPublicacao();

        PublicationEvent event = new PublicationEvent(
                "publication.discovered",
                LocalDateTime.now(),
                publication,
                null
        );

        assertDoesNotThrow(() -> listener.consume(event, Map.of()));

        verify(publicationService).processEvent(event);
        verifyNoInteractions(retryPublisher);
    }

    private PublicationEvent criarEvento() {
        return new PublicationEvent("publication.discovered", LocalDateTime.now(), criarPublicacao(), null);
    }

    static Stream<RuntimeException> falhasTransitorias() {
        return Stream.of(new TransientDataAccessResourceException("banco indisponivel"),
                new RecoverableDataAccessException("conexao perdida"),
                new CannotCreateTransactionException("transacao indisponivel"),
                new TransactionTimedOutException("timeout"),
                new AmqpConnectException(new ConnectException("broker indisponivel")),
                new RuntimeException(new SQLTransientConnectionException("banco indisponivel")),
                new RuntimeException(new SQLRecoverableException("conexao perdida")),
                new RuntimeException(new SocketTimeoutException("timeout")),
                new IllegalArgumentException("wrapper", new ConnectException("conexao perdida")));
    }

    @ParameterizedTest
    @MethodSource("falhasTransitorias")
    void deveEnviarFalhasTransitoriasConhecidasParaRetry(RuntimeException falha) {
        PublicationEvent event = criarEvento();
        doThrow(falha).when(publicationService).processEvent(event);
        listener.consume(event, Map.of());
        verify(retryPublisher).publishToRetry(same(event), argThat(headers ->
                !headers.containsKey(PublicationRetryPublisher.RETRY_COUNT_HEADER)
                        && headers.get(PublicationEventListener.ORIGINAL_ERROR_TYPE_HEADER)
                        .equals(falha.getClass().getName())));
        verifyNoMoreInteractions(retryPublisher);
    }

    static Stream<RuntimeException> falhasPermanentes() {
        return Stream.of(new IllegalArgumentException("senha=segredo"),
                new DataIntegrityViolationException("SQL com dados sensiveis"),
                new ConstraintViolationException("payload sensivel", Set.of()),
                new MessageConversionException("payload sensivel"),
                new RuntimeException("wrapper", new IllegalArgumentException("segredo")));
    }

    @ParameterizedTest
    @MethodSource("falhasPermanentes")
    void deveEnviarFalhasPermanentesParaDlqSemExporMensagem(RuntimeException falha) {
        PublicationEvent event = criarEvento();
        doThrow(falha).when(publicationService).processEvent(event);
        listener.consume(event, Map.of("authorization", "Bearer segredo", "x-api-key", "segredo",
                "x-original-source", "collector", PublicationRetryPublisher.ERROR_MESSAGE_HEADER, "segredo"));
        ArgumentCaptor<Throwable> erro = ArgumentCaptor.forClass(Throwable.class);
        verify(retryPublisher).publishToDlq(same(event), argThat(headers ->
                !headers.containsKey("authorization") && !headers.containsKey("x-api-key")
                        && headers.get("x-original-source").equals("collector")
                        && !headers.get(PublicationRetryPublisher.ERROR_MESSAGE_HEADER).toString().contains("segredo")
                        && headers.get(PublicationEventListener.ORIGINAL_ERROR_TYPE_HEADER)
                        .equals(falha.getClass().getName())), erro.capture());
        assertEquals("Falha de processamento; encaminhada à DLQ", erro.getValue().getMessage());
        assertNull(erro.getValue().getCause());
        verifyNoMoreInteractions(retryPublisher);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void devePermitirAteTresNovasTentativasAlemDaOriginal(int contador) {
        PublicationEvent event = criarEvento();
        doThrow(new TransientDataAccessResourceException("falha")).when(publicationService).processEvent(event);
        Map<String, Object> headers = Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER, contador,
                "traceparent", "trace", "x-death", List.of(Map.of("count", 2L)));
        listener.consume(event, headers);
        verify(retryPublisher).publishToRetry(same(event), argThat(result ->
                result.get(PublicationRetryPublisher.RETRY_COUNT_HEADER).equals(contador)
                        && result.get("traceparent").equals("trace")
                        && result.get("x-death").equals(headers.get("x-death"))));
        assertEquals(contador, headers.get(PublicationRetryPublisher.RETRY_COUNT_HEADER));
        verifyNoMoreInteractions(retryPublisher);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, Integer.MAX_VALUE})
    void deveEnviarFalhaTransitoriaParaDlqAoEsgotarTentativas(int contador) {
        PublicationEvent event = criarEvento();
        doThrow(new TransientDataAccessResourceException("falha")).when(publicationService).processEvent(event);
        listener.consume(event, Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER, contador));
        verify(retryPublisher).publishToDlq(same(event), argThat(headers ->
                headers.get(PublicationRetryPublisher.RETRY_COUNT_HEADER).equals(contador)), any(Throwable.class));
        verifyNoMoreInteractions(retryPublisher);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void deveLimitarRetryDeFalhasNaoClassificadas(int contador) {
        PublicationEvent event = criarEvento();
        doThrow(new IllegalStateException("falha desconhecida")).when(publicationService).processEvent(event);
        listener.consume(event, Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER, contador));
        if (contador < PublicationEventListener.MAX_RETRIES) {
            verify(retryPublisher).publishToRetry(same(event), anyMap());
        } else {
            verify(retryPublisher).publishToDlq(same(event), anyMap(), any(Throwable.class));
        }
        verifyNoMoreInteractions(retryPublisher);
    }

    @Test
    void deveAceitarContadorDecimalEmString() {
        PublicationEvent event = criarEvento();
        doThrow(new RuntimeException("falha")).when(publicationService).processEvent(event);
        listener.consume(event, Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER, "2"));
        verify(retryPublisher).publishToRetry(same(event), argThat(headers ->
                "2".equals(headers.get(PublicationRetryPublisher.RETRY_COUNT_HEADER))));
    }

    static Stream<Object> contadoresInvalidos() {
        return Stream.of("invalido sensivel", "-1", -1, 1.5, 2147483648L, "2147483648", true, null);
    }

    @ParameterizedTest
    @MethodSource("contadoresInvalidos")
    void deveEnviarContadorInvalidoParaDlqSemProcessarOuReiniciarRetry(Object contador) {
        PublicationEvent event = criarEvento();
        Map<String, Object> headers = new HashMap<>();
        headers.put(PublicationRetryPublisher.RETRY_COUNT_HEADER, contador);
        listener.consume(event, headers);
        verifyNoInteractions(publicationService);
        verify(retryPublisher).publishToDlq(same(event), argThat(result ->
                result.get(PublicationRetryPublisher.RETRY_COUNT_HEADER).equals(3)
                        && Boolean.TRUE.equals(result.get(PublicationEventListener.INVALID_RETRY_COUNT_HEADER))),
                argThat(erro -> "Contador de retry inválido".equals(erro.getMessage())));
        assertEquals(contador, headers.get(PublicationRetryPublisher.RETRY_COUNT_HEADER));
        verifyNoMoreInteractions(retryPublisher);
    }

    @Test
    void deveEnviarPayloadSemPublicacaoParaDlq() {
        PublicationEvent event = new PublicationEvent("publication.discovered", LocalDateTime.now(), null, null);
        listener.consume(event, Map.of());
        verifyNoInteractions(publicationService);
        verify(retryPublisher).publishToDlq(same(event), anyMap(), any(Throwable.class));
    }

    @Test
    void devePreservarProcessamentoBemSucedidoMesmoNaUltimaTentativa() {
        PublicationEvent event = criarEvento();
        listener.consume(event, Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER, 3));
        verify(publicationService).processEvent(event);
        verifyNoInteractions(retryPublisher);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void devePropagarFalhaDeRepublicacaoEForcarRequeueMesmoComDefaultFalse(boolean dlq) {
        PublicationEvent event = criarEvento();
        doThrow(new RuntimeException("processamento falhou")).when(publicationService).processEvent(event);
        AmqpException falhaPublicacao = new AmqpException("broker indisponivel");
        if (dlq) {
            doThrow(falhaPublicacao).when(retryPublisher).publishToDlq(same(event), anyMap(), any(Throwable.class));
        } else {
            doThrow(falhaPublicacao).when(retryPublisher).publishToRetry(same(event), anyMap());
        }
        ImmediateRequeueAmqpException falha = assertThrows(ImmediateRequeueAmqpException.class,
                () -> listener.consume(event, Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER, dlq ? 3 : 0)));
        assertSame(falhaPublicacao, falha.getCause());
        assertTrue(ContainerUtils.shouldRequeue(false, falha, LogFactory.getLog(getClass())));
        verify(publicationService).processEvent(event);
    }

    @Test
    void deveManterOriginalQuandoPublicacaoDaDlqParaContadorInvalidoFalhar() {
        PublicationEvent event = criarEvento();
        AmqpException falhaPublicacao = new AmqpException("sem rota");
        doThrow(falhaPublicacao).when(retryPublisher).publishToDlq(same(event), anyMap(), any(Throwable.class));
        ImmediateRequeueAmqpException falha = assertThrows(ImmediateRequeueAmqpException.class,
                () -> listener.consume(event, Map.of(PublicationRetryPublisher.RETRY_COUNT_HEADER, "invalido")));
        assertSame(falhaPublicacao, falha.getCause());
        verifyNoInteractions(publicationService);
    }

    @Test
    void deveEvitarDescartePeloErrorHandlerQuandoRepublicacaoFalharComXDeath() {
        PublicationEvent event = criarEvento();
        doThrow(new IllegalArgumentException("falha permanente")).when(publicationService).processEvent(event);
        MessageConversionException falhaPublicacao = new MessageConversionException("envio falhou");
        doThrow(falhaPublicacao).when(retryPublisher).publishToDlq(same(event), anyMap(), any(Throwable.class));
        ImmediateRequeueAmqpException falha = assertThrows(ImmediateRequeueAmqpException.class,
                () -> listener.consume(event, Map.of("x-death", List.of(Map.of("count", 1L)))));
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-death", List.of(Map.of("count", 1L)));
        ListenerExecutionFailedException wrapped = new ListenerExecutionFailedException(
                "listener falhou", falha, new Message(new byte[0], properties));
        assertDoesNotThrow(() -> new ConditionalRejectingErrorHandler().handleError(wrapped));
        assertTrue(ContainerUtils.shouldRequeue(false, wrapped, LogFactory.getLog(getClass())));
        assertFalse(ContainerUtils.isImmediateAcknowledge(wrapped));
        assertSame(falhaPublicacao, falha.getCause());
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
