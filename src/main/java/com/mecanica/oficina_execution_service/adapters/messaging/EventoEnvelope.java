package com.mecanica.oficina_execution_service.adapters.messaging;

import java.time.Instant;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/** Envelope padrão dos eventos de integração entre serviços (ADR 005 em oficina-app-api, seção 3). */
public record EventoEnvelope(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String sagaId,
        JsonNode payload) {

    public static final int VERSAO_ATUAL = 1;

    public EventoEnvelope {
        Objects.requireNonNull(eventId, "eventId é obrigatório");
        Objects.requireNonNull(eventType, "eventType é obrigatório");
        Objects.requireNonNull(sagaId, "sagaId é obrigatório");
    }
}
