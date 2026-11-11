package com.mecanica.oficina_execution_service.adapters.web.dto;

import com.mecanica.oficina_execution_service.domain.execucao.EtapaExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusTarefa;
import java.time.Instant;
import java.util.List;

public record ExecucaoResponse(
        String ordemServicoId,
        StatusExecucao status,
        List<TarefaResponse> tarefas,
        List<AndamentoResponse> andamentos,
        String motivoFalha,
        Instant criadaEm,
        Instant enfileiradaEm,
        Instant iniciadaEm,
        Instant finalizadaEm) {

    public record TarefaResponse(String servicoId, String descricao, int quantidade, StatusTarefa status,
                                 Instant iniciadaEm, Instant concluidaEm) {
    }

    public record AndamentoResponse(EtapaExecucao etapa, String descricao, Instant registradoEm) {
    }
}
