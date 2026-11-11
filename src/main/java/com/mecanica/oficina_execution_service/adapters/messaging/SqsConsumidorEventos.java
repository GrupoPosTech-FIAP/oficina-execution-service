package com.mecanica.oficina_execution_service.adapters.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consome a fila SQS do serviço com long polling. A mensagem só é removida da fila depois de
 * processada com sucesso; se o processamento falhar ela volta a ficar visível e, após
 * {@code maxReceiveCount} tentativas, vai para a DLQ.
 */
public class SqsConsumidorEventos {

    private static final Logger log = LoggerFactory.getLogger(SqsConsumidorEventos.class);

    private final SqsClient sqsClient;
    private final ProcessadorEventos processador;
    private final JsonMapper jsonMapper;
    private final MensageriaProperties properties;

    public SqsConsumidorEventos(SqsClient sqsClient,
                                ProcessadorEventos processador,
                                JsonMapper jsonMapper,
                                MensageriaProperties properties) {
        this.sqsClient = sqsClient;
        this.processador = processador;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${mensageria.intervalo-consumo-ms:100}")
    public void consumir() {
        sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(properties.filaUrl())
                        .maxNumberOfMessages(properties.maxMensagens())
                        .waitTimeSeconds(properties.esperaSegundos())
                        .messageAttributeNames("All")
                        .build())
                .messages()
                .forEach(this::tratar);
    }

    private void tratar(Message mensagem) {
        EventoEnvelope evento;
        try {
            evento = jsonMapper.readValue(mensagem.body(), EventoEnvelope.class);
        } catch (RuntimeException e) {
            // Mensagem malformada nunca vai ser processada: deixa expirar as tentativas e ir para a DLQ
            log.error("[SAGA_MENSAGEM_INVALIDA] message_id={} erro={}", mensagem.messageId(), e.getMessage());
            return;
        }

        try {
            processador.processar(evento);
        } catch (RuntimeException e) {
            log.error("[SAGA_EVENTO_FALHA_PROCESSAMENTO] event_type={} event_id={} saga_id={} erro={}",
                    evento.eventType(), evento.eventId(), evento.sagaId(), e.getMessage(), e);
            return;
        }

        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(properties.filaUrl())
                .receiptHandle(mensagem.receiptHandle())
                .build());
    }
}
