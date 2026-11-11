package com.mecanica.oficina_execution_service.adapters.web;

import com.mecanica.oficina_execution_service.adapters.web.dto.ExecucaoResponse;
import com.mecanica.oficina_execution_service.adapters.web.dto.ExecucaoResponse.AndamentoResponse;
import com.mecanica.oficina_execution_service.adapters.web.dto.ExecucaoResponse.TarefaResponse;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;

final class ExecucaoPresenter {

    private ExecucaoPresenter() {
    }

    static ExecucaoResponse apresentar(Execucao execucao) {
        return new ExecucaoResponse(
                execucao.getOrdemServicoId(),
                execucao.getStatus(),
                execucao.getTarefas().stream()
                        .map(t -> new TarefaResponse(t.servicoId(), t.descricao(), t.quantidade(), t.status(),
                                t.iniciadaEm(), t.concluidaEm()))
                        .toList(),
                execucao.getAndamentos().stream()
                        .map(a -> new AndamentoResponse(a.etapa(), a.descricao(), a.registradoEm()))
                        .toList(),
                execucao.getMotivoFalha(),
                execucao.getCriadaEm(),
                execucao.getEnfileiradaEm(),
                execucao.getIniciadaEm(),
                execucao.getFinalizadaEm());
    }
}
