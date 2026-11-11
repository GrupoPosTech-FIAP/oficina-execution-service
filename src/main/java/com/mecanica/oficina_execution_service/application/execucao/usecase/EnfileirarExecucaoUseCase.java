package com.mecanica.oficina_execution_service.application.execucao.usecase;

import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * {@code PagamentoConfirmado} → coloca a execução na fila. Pagamento repetido (a execução já saiu de
 * {@code PLANEJADA}) é ignorado.
 */
public class EnfileirarExecucaoUseCase {

    public enum Resultado { ENFILEIRADA, ENFILEIRADA_SEM_PLANO, JA_ENFILEIRADA }

    private final ExecucaoGateway gateway;
    private final Clock clock;

    public EnfileirarExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public Resultado executar(String ordemServicoId) {
        Optional<Execucao> existente = gateway.buscar(ordemServicoId);
        if (existente.isEmpty()) {
            gateway.salvar(Execucao.enfileirarSemPlano(ordemServicoId, clock.instant()), List.of());
            return Resultado.ENFILEIRADA_SEM_PLANO;
        }

        Execucao execucao = existente.get();
        if (execucao.getStatus() != StatusExecucao.PLANEJADA) {
            return Resultado.JA_ENFILEIRADA;
        }
        execucao.enfileirar(clock.instant());
        gateway.salvar(execucao, List.of());
        return Resultado.ENFILEIRADA;
    }
}
