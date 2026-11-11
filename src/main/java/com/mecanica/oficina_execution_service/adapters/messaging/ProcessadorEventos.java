package com.mecanica.oficina_execution_service.adapters.messaging;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Entrega um evento recebido ao manipulador do seu tipo.
 *
 * <p>Diferente dos serviços com Postgres, aqui não há tabela {@code eventos_processados}: os dois
 * eventos consumidos só criam ou avançam a execução de uma OS, e os casos de uso ignoram o evento
 * quando a execução já passou daquele ponto. A gravação condicional no DynamoDB (versão) cobre duas
 * entregas simultâneas do mesmo evento.
 */
@Component
public class ProcessadorEventos {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorEventos.class);

    public enum Resultado { PROCESSADO, IGNORADO }

    private final Map<String, ManipuladorEvento> manipuladores;

    public ProcessadorEventos(List<ManipuladorEvento> manipuladores) {
        this.manipuladores = manipuladores.stream()
                .flatMap(m -> m.eventTypes().stream().map(tipo -> Map.entry(tipo, m)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Resultado processar(EventoEnvelope evento) {
        ManipuladorEvento manipulador = manipuladores.get(evento.eventType());
        if (manipulador == null) {
            log.warn("[SAGA_EVENTO_SEM_MANIPULADOR] event_type={} event_id={} saga_id={}",
                    evento.eventType(), evento.eventId(), evento.sagaId());
            return Resultado.IGNORADO;
        }

        manipulador.tratar(evento);
        log.info("[SAGA_EVENTO_CONSUMIDO] event_type={} event_id={} saga_id={} producer={}",
                evento.eventType(), evento.eventId(), evento.sagaId(), evento.producer());
        return Resultado.PROCESSADO;
    }
}
