package com.mecanica.oficina_execution_service.adapters.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static com.mecanica.oficina_execution_service.adapters.messaging.MensageriaFixture.JSON;
import static com.mecanica.oficina_execution_service.adapters.messaging.MensageriaFixture.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqsConsumidorEventosTest {

    private SqsClient sqsClient;
    private ProcessadorEventos processador;
    private SqsConsumidorEventos consumidor;

    @BeforeEach
    void setUp() {
        sqsClient = mock(SqsClient.class);
        processador = mock(ProcessadorEventos.class);
        consumidor = new SqsConsumidorEventos(sqsClient, processador, JSON, MensageriaFixture.properties());
    }

    private void filaCom(String corpo) {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().messageId("m-1").receiptHandle("rh-1").body(corpo).build())
                .build());
    }

    @Test
    void processaERemoveAMensagemDaFila() {
        EventoEnvelope evento = envelope("ev-1", "OrcamentoGerado");
        filaCom(JSON.writeValueAsString(evento));

        consumidor.consumir();

        ArgumentCaptor<ReceiveMessageRequest> receive = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsClient).receiveMessage(receive.capture());
        assertThat(receive.getValue().queueUrl()).isEqualTo(MensageriaFixture.properties().filaUrl());
        assertThat(receive.getValue().waitTimeSeconds()).isEqualTo(10);
        assertThat(receive.getValue().maxNumberOfMessages()).isEqualTo(10);

        ArgumentCaptor<EventoEnvelope> processado = ArgumentCaptor.forClass(EventoEnvelope.class);
        verify(processador).processar(processado.capture());
        assertThat(processado.getValue().eventId()).isEqualTo("ev-1");
        assertThat(processado.getValue().payload().get("orcamentoId").asString()).isEqualTo("orc-1");

        ArgumentCaptor<DeleteMessageRequest> delete = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(delete.capture());
        assertThat(delete.getValue().receiptHandle()).isEqualTo("rh-1");
    }

    @Test
    void falhaNoProcessamentoMantemAMensagemNaFilaParaRetentativa() {
        filaCom(JSON.writeValueAsString(envelope("ev-1", "OrcamentoGerado")));
        when(processador.processar(any())).thenThrow(new IllegalStateException("banco indisponível"));

        consumidor.consumir();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void mensagemMalformadaNaoEProcessadaNemRemovida() {
        filaCom("isto não é json");

        consumidor.consumir();

        verify(processador, never()).processar(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void envelopeSemCamposObrigatoriosEMalformado() {
        filaCom("{\"eventType\":\"OrcamentoGerado\"}");

        consumidor.consumir();

        verify(processador, never()).processar(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
