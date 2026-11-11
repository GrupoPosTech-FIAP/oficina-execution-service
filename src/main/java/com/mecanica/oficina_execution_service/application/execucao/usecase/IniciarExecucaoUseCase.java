package com.mecanica.oficina_execution_service.application.execucao.usecase;

import com.mecanica.oficina_execution_service.application.evento.EventoSaga;
import com.mecanica.oficina_execution_service.application.evento.EventosSaga;
import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/** NA_FILA → EM_EXECUCAO e publica {@code ExecucaoIniciada} (a OS vai para EM_EXECUCAO). */
public class IniciarExecucaoUseCase {

    private final ExecucaoGateway gateway;
    private final Clock clock;

    public IniciarExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public Execucao executar(String ordemServicoId) {
        Execucao execucao = gateway.encontrarOuLancar(ordemServicoId);
        execucao.iniciar(clock.instant());
        gateway.salvar(execucao, List.of(new EventoSaga(EventosSaga.EXECUCAO_INICIADA, ordemServicoId, Map.of(
                "ordemServicoId", ordemServicoId,
                "iniciadaEm", execucao.getIniciadaEm().toString()))));
        return execucao;
    }
}
