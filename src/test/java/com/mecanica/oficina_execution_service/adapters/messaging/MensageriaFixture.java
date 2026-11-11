package com.mecanica.oficina_execution_service.adapters.messaging;

import java.time.Instant;
import tools.jackson.databind.json.JsonMapper;

final class MensageriaFixture {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Instant AGORA = Instant.parse("2026-11-07T14:32:10Z");

    private MensageriaFixture() {
    }

    static MensageriaProperties properties() {
        return new MensageriaProperties(true, "oficina-execution-service",
                "arn:aws:sns:us-east-1:000000000000:oficina-execution-events",
                "http://localhost:4566/000000000000/oficina-execution-queue",
                null, null, null, null, null);
    }

    static EventoEnvelope envelope(String eventId, String eventType) {
        return new EventoEnvelope(eventId, eventType, 1, AGORA, "oficina-billing-service", "os-1",
                JSON.readTree("{\"orcamentoId\":\"orc-1\"}"));
    }
}
