package com.mecanica.oficina_execution_service.domain.execucao;

public class TarefaNaoEncontradaException extends RuntimeException {

    public TarefaNaoEncontradaException(String ordemServicoId, String servicoId) {
        super("A execução da OS " + ordemServicoId + " não tem a tarefa do serviço " + servicoId);
    }
}
