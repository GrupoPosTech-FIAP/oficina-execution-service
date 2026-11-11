package com.mecanica.oficina_execution_service.application.execucao.usecase;

import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import java.util.List;

public class ConsultarExecucaoUseCase {

    private final ExecucaoGateway gateway;

    public ConsultarExecucaoUseCase(ExecucaoGateway gateway) {
        this.gateway = gateway;
    }

    public Execucao buscar(String ordemServicoId) {
        return gateway.encontrarOuLancar(ordemServicoId);
    }

    /** Fila de execução: as mais antigas primeiro. */
    public List<Execucao> listar(StatusExecucao status) {
        return gateway.listarPorStatus(status);
    }
}
