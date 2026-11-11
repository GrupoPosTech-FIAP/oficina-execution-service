package com.mecanica.oficina_execution_service.application.execucao.usecase;

import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusTarefa;
import com.mecanica.oficina_execution_service.domain.execucao.Tarefa;
import java.time.Duration;
import java.util.List;

/**
 * Tempo médio de execução de um serviço do catálogo, medido nas tarefas das execuções concluídas
 * (do início ao fim da tarefa). Lê todas as execuções concluídas: serve para o volume da oficina,
 * e não para relatórios sobre um histórico grande.
 */
public class ConsultarTempoMedioServicoUseCase {

    public record TempoMedioServico(String servicoId, String descricao, int execucoes, double tempoMedioEmMinutos) {
    }

    private final ExecucaoGateway gateway;

    public ConsultarTempoMedioServicoUseCase(ExecucaoGateway gateway) {
        this.gateway = gateway;
    }

    public TempoMedioServico executar(String servicoId) {
        List<Tarefa> tarefas = gateway.listarPorStatus(StatusExecucao.CONCLUIDA).stream()
                .flatMap(execucao -> execucao.getTarefas().stream())
                .filter(t -> t.servicoId().equals(servicoId) && t.status() == StatusTarefa.CONCLUIDA)
                .toList();

        double media = tarefas.stream()
                .mapToLong(t -> Duration.between(t.iniciadaEm(), t.concluidaEm()).toSeconds())
                .average()
                .orElse(0) / 60.0;
        String descricao = tarefas.stream().map(Tarefa::descricao).filter(d -> d != null).findFirst().orElse(null);
        return new TempoMedioServico(servicoId, descricao, tarefas.size(), Math.round(media * 100) / 100.0);
    }
}
