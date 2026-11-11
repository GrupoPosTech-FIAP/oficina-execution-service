package com.mecanica.oficina_execution_service.adapters.messaging;

import com.mecanica.oficina_execution_service.application.evento.EventoSaga;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Monta o envelope padrão (ADR 005, seção 3) de um evento a publicar. */
@Component
public class FabricaEnvelopes {

    private final JsonMapper jsonMapper;
    private final MensageriaProperties properties;
    private final Clock clock;

    public FabricaEnvelopes(JsonMapper jsonMapper, MensageriaProperties properties, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public EventoEnvelope criar(EventoSaga evento) {
        return new EventoEnvelope(
                UUID.randomUUID().toString(),
                evento.eventType(),
                EventoEnvelope.VERSAO_ATUAL,
                clock.instant(),
                properties.produtor(),
                evento.sagaId(),
                jsonMapper.valueToTree(evento.payload()));
    }

    public String serializar(EventoEnvelope envelope) {
        return jsonMapper.writeValueAsString(envelope);
    }
}
