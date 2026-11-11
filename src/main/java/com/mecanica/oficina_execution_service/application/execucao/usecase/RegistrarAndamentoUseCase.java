package com.mecanica.oficina_execution_service.application.execucao.usecase;

import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.domain.execucao.EtapaExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import java.time.Clock;
import java.util.List;

/** Anotação do mecânico sobre o diagnóstico ou o reparo em andamento. */
public class RegistrarAndamentoUseCase {

    private final ExecucaoGateway gateway;
    private final Clock clock;

    public RegistrarAndamentoUseCase(ExecucaoGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public Execucao executar(String ordemServicoId, EtapaExecucao etapa, String descricao) {
        Execucao execucao = gateway.encontrarOuLancar(ordemServicoId);
        execucao.registrarAndamento(etapa, descricao, clock.instant());
        gateway.salvar(execucao, List.of());
        return execucao;
    }
}
