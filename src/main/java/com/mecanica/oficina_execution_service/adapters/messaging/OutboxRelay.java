package com.mecanica.oficina_execution_service.adapters.messaging;

import com.mecanica.oficina_execution_service.adapters.persistence.DynamoOutboxRepository;
import com.mecanica.oficina_execution_service.adapters.persistence.DynamoOutboxRepository.EventoPendente;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * Lê os eventos pendentes da outbox e publica no tópico SNS do serviço, em ordem de criação.
 * Falhas não interrompem o lote: o evento fica pendente e é retentado no próximo ciclo.
 * Entrega é at-least-once — os consumidores deduplicam pelo {@code eventId}.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final String ATRIBUTO_EVENT_TYPE = "eventType";

    private final DynamoOutboxRepository repository;
    private final SnsClient snsClient;
    private final MensageriaProperties properties;
    private final Clock clock;

    public OutboxRelay(DynamoOutboxRepository repository,
                       SnsClient snsClient,
                       MensageriaProperties properties,
                       Clock clock) {
        this.repository = repository;
        this.snsClient = snsClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mensageria.intervalo-outbox-ms:1000}")
    public void publicarPendentes() {
        repository.pendentes(properties.loteOutbox()).forEach(this::publicar);
    }

    private void publicar(EventoPendente evento) {
        try {
            snsClient.publish(PublishRequest.builder()
                    .topicArn(properties.topicoArn())
                    .message(evento.envelope())
                    .messageAttributes(Map.of(ATRIBUTO_EVENT_TYPE, MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(evento.eventType())
                            .build()))
                    .build());
        } catch (RuntimeException e) {
            repository.registrarFalha(evento.eventId(), truncar(e.getMessage()));
            log.warn("[SAGA_EVENTO_FALHA_PUBLICACAO] event_type={} event_id={} saga_id={} tentativas={} erro={}",
                    evento.eventType(), evento.eventId(), evento.sagaId(), evento.tentativas() + 1, e.getMessage());
            return;
        }
        repository.marcarPublicado(evento.eventId(), clock.instant());
        log.info("[SAGA_EVENTO_PUBLICADO] event_type={} event_id={} saga_id={}",
                evento.eventType(), evento.eventId(), evento.sagaId());
    }

    private static String truncar(String mensagem) {
        if (mensagem == null) return null;
        return mensagem.length() <= 1000 ? mensagem : mensagem.substring(0, 1000);
    }
}
