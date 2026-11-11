package com.mecanica.oficina_execution_service.application.execucao;

public class ExecucaoNaoEncontradaException extends RuntimeException {

    public ExecucaoNaoEncontradaException(String ordemServicoId) {
        super("Nenhuma execução para a OS " + ordemServicoId);
    }
}
