package com.mecanica.oficina_execution_service.application.execucao.usecase;

import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.application.execucao.input.TarefaInput;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import com.mecanica.oficina_execution_service.domain.execucao.Tarefa;
import java.time.Clock;
import java.util.List;

/**
 * {@code DiagnosticoConcluido} → guarda as tarefas da OS ({@code PLANEJADA}) para quando o pagamento
 * for confirmado. Se a execução já existe (evento repetido), nada muda.
 */
public class PlanejarExecucaoUseCase {

    public enum Resultado { PLANEJADA, JA_EXISTENTE }

    private final ExecucaoGateway gateway;
    private final Clock clock;

    public PlanejarExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public Resultado executar(String ordemServicoId, List<TarefaInput> servicos) {
        if (gateway.buscar(ordemServicoId).isPresent()) {
            return Resultado.JA_EXISTENTE;
        }
        List<Tarefa> tarefas = servicos.stream()
                .map(s -> Tarefa.pendente(s.servicoId(), s.descricao(), s.quantidade()))
                .toList();
        gateway.salvar(Execucao.planejar(ordemServicoId, tarefas, clock.instant()), List.of());
        return Resultado.PLANEJADA;
    }
}
