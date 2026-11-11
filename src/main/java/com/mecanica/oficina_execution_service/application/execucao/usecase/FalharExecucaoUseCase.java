package com.mecanica.oficina_execution_service.application.execucao.usecase;

import com.mecanica.oficina_execution_service.application.evento.EventoSaga;
import com.mecanica.oficina_execution_service.application.evento.EventosSaga;
import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Reparo inviável: EM_EXECUCAO → FALHOU e publica {@code ExecucaoFalhou}, que dispara as compensações
 * (o inventory estorna os insumos, o billing reembolsa o pagamento e a OS é cancelada).
 */
public class FalharExecucaoUseCase {

    private final ExecucaoGateway gateway;
    private final Clock clock;

    public FalharExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public Execucao executar(String ordemServicoId, String motivo) {
        Execucao execucao = gateway.encontrarOuLancar(ordemServicoId);
        execucao.falhar(motivo, clock.instant());
        gateway.salvar(execucao, List.of(new EventoSaga(EventosSaga.EXECUCAO_FALHOU, ordemServicoId, Map.of(
                "ordemServicoId", ordemServicoId,
                "motivo", motivo,
                "falhouEm", execucao.getFinalizadaEm().toString()))));
        return execucao;
    }
}
