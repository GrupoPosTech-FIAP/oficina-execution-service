package com.mecanica.oficina_execution_service.application.execucao.usecase;

import com.mecanica.oficina_execution_service.application.evento.EventoSaga;
import com.mecanica.oficina_execution_service.application.evento.EventosSaga;
import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/** EM_EXECUCAO → CONCLUIDA e publica {@code ExecucaoConcluida} (a OS vai para FINALIZADA). */
public class ConcluirExecucaoUseCase {

    private final ExecucaoGateway gateway;
    private final Clock clock;

    public ConcluirExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public Execucao executar(String ordemServicoId) {
        Execucao execucao = gateway.encontrarOuLancar(ordemServicoId);
        execucao.concluir(clock.instant());
        gateway.salvar(execucao, List.of(new EventoSaga(EventosSaga.EXECUCAO_CONCLUIDA, ordemServicoId, Map.of(
                "ordemServicoId", ordemServicoId,
                "concluidaEm", execucao.getFinalizadaEm().toString()))));
        return execucao;
    }
}
