package com.mecanica.oficina_execution_service.domain.execucao;

import java.time.Instant;
import java.util.Objects;

/** Um serviço do orçamento a ser feito na execução (vem do {@code DiagnosticoConcluido}). */
public record Tarefa(
        String servicoId,
        String descricao,
        int quantidade,
        StatusTarefa status,
        Instant iniciadaEm,
        Instant concluidaEm) {

    public Tarefa {
        if (servicoId == null || servicoId.isBlank()) {
            throw new IllegalArgumentException("O id do serviço é obrigatório");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade do serviço deve ser maior que zero");
        }
        Objects.requireNonNull(status, "O status da tarefa é obrigatório");
    }

    public static Tarefa pendente(String servicoId, String descricao, int quantidade) {
        return new Tarefa(servicoId, descricao, quantidade, StatusTarefa.PENDENTE, null, null);
    }

    Tarefa iniciar(Instant agora) {
        if (status != StatusTarefa.PENDENTE) {
            throw new IllegalStateException("A tarefa " + servicoId + " não está PENDENTE");
        }
        return new Tarefa(servicoId, descricao, quantidade, StatusTarefa.EM_ANDAMENTO, agora, null);
    }

    Tarefa concluir(Instant agora) {
        if (status == StatusTarefa.CONCLUIDA) {
            throw new IllegalStateException("A tarefa " + servicoId + " já foi concluída");
        }
        // concluir direto de PENDENTE conta o início no mesmo instante
        return new Tarefa(servicoId, descricao, quantidade, StatusTarefa.CONCLUIDA,
                iniciadaEm != null ? iniciadaEm : agora, agora);
    }
}
