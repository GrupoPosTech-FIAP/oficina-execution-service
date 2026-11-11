package com.mecanica.oficina_execution_service.application.execucao.usecase;

import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import java.time.Clock;
import java.util.List;

/** Andamento de cada serviço da OS durante a execução. Não publica evento: a OS só acompanha o todo. */
public class AtualizarTarefaUseCase {

    private final ExecucaoGateway gateway;
    private final Clock clock;

    public AtualizarTarefaUseCase(ExecucaoGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public Execucao iniciar(String ordemServicoId, String servicoId) {
        Execucao execucao = gateway.encontrarOuLancar(ordemServicoId);
        execucao.iniciarTarefa(servicoId, clock.instant());
        gateway.salvar(execucao, List.of());
        return execucao;
    }

    public Execucao concluir(String ordemServicoId, String servicoId) {
        Execucao execucao = gateway.encontrarOuLancar(ordemServicoId);
        execucao.concluirTarefa(servicoId, clock.instant());
        gateway.salvar(execucao, List.of());
        return execucao;
    }
}
