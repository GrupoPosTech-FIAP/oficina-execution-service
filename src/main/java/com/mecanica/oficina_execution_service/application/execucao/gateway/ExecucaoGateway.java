package com.mecanica.oficina_execution_service.application.execucao.gateway;

import com.mecanica.oficina_execution_service.application.evento.EventoSaga;
import com.mecanica.oficina_execution_service.application.execucao.ExecucaoNaoEncontradaException;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import java.util.List;
import java.util.Optional;

/** Porta de saída para o armazenamento das execuções e da outbox de eventos. */
public interface ExecucaoGateway {

    Optional<Execucao> buscar(String ordemServicoId);

    default Execucao encontrarOuLancar(String ordemServicoId) {
        return buscar(ordemServicoId).orElseThrow(() -> new ExecucaoNaoEncontradaException(ordemServicoId));
    }

    /** Execuções no status informado, da mais antiga para a mais nova (ordem da fila). */
    List<Execucao> listarPorStatus(StatusExecucao status);

    /**
     * Grava a execução e os eventos na outbox numa única transação: ou tudo é confirmado, ou nada.
     * Execução nova ({@code versao} 0) só é criada se ainda não existir; execução existente só é
     * gravada se a versão no banco for a mesma que foi lida.
     *
     * @throws ConflitoDeConcorrenciaException se outra gravação aconteceu antes
     */
    void salvar(Execucao execucao, List<EventoSaga> eventos);
}
