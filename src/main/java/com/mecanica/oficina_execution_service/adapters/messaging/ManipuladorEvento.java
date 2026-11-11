package com.mecanica.oficina_execution_service.adapters.messaging;

import java.util.Set;

/**
 * Adaptador de entrada para um ou mais tipos de evento — o equivalente de um controller para a fila.
 * Cada implementação traduz o payload e chama o caso de uso correspondente.
 *
 * <p>O caso de uso precisa ser idempotente: o SQS pode entregar o mesmo evento mais de uma vez.
 * Lançar exceção devolve a mensagem para a fila.
 */
public interface ManipuladorEvento {

    /** Tipos de evento (catálogo da ADR 005) tratados por este manipulador. */
    Set<String> eventTypes();

    void tratar(EventoEnvelope evento);
}
