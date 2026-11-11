package com.mecanica.oficina_execution_service.application.evento;

import java.util.Map;
import java.util.Objects;

/**
 * Evento de integração a publicar, gravado na outbox junto com a execução que o originou.
 *
 * @param eventType nome do evento do catálogo da ADR 005 (ex.: {@code ExecucaoIniciada})
 * @param sagaId    identificador da Saga — o id da ordem de serviço
 * @param payload   dados do evento, serializados como JSON
 */
public record EventoSaga(String eventType, String sagaId, Map<String, Object> payload) {

    public EventoSaga {
        Objects.requireNonNull(eventType, "eventType é obrigatório");
        Objects.requireNonNull(sagaId, "sagaId é obrigatório");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
