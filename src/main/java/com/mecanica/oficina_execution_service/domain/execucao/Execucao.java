package com.mecanica.oficina_execution_service.domain.execucao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Execução de uma ordem de serviço na oficina. O id é o próprio {@code ordemServicoId}, que também é o
 * {@code sagaId}: há no máximo uma execução por OS.
 *
 * <p>{@code versao} é o controle de concorrência otimista: a gravação só passa se ninguém tiver
 * gravado a mesma execução desde a leitura.
 */
public class Execucao {

    private final String ordemServicoId;
    private StatusExecucao status;
    private final List<Tarefa> tarefas;
    private final List<Andamento> andamentos;
    private String motivoFalha;
    private final Instant criadaEm;
    private Instant enfileiradaEm;
    private Instant iniciadaEm;
    private Instant finalizadaEm;
    private final long versao;

    public Execucao(String ordemServicoId, StatusExecucao status, List<Tarefa> tarefas, List<Andamento> andamentos,
                    String motivoFalha, Instant criadaEm, Instant enfileiradaEm, Instant iniciadaEm,
                    Instant finalizadaEm, long versao) {
        if (ordemServicoId == null || ordemServicoId.isBlank()) {
            throw new IllegalArgumentException("O id da ordem de serviço é obrigatório");
        }
        this.ordemServicoId = ordemServicoId;
        this.status = Objects.requireNonNull(status, "O status é obrigatório");
        this.tarefas = new ArrayList<>(tarefas == null ? List.of() : tarefas);
        this.andamentos = new ArrayList<>(andamentos == null ? List.of() : andamentos);
        this.motivoFalha = motivoFalha;
        this.criadaEm = Objects.requireNonNull(criadaEm, "A data de criação é obrigatória");
        this.enfileiradaEm = enfileiradaEm;
        this.iniciadaEm = iniciadaEm;
        this.finalizadaEm = finalizadaEm;
        this.versao = versao;
    }

    /** Diagnóstico concluído: guarda as tarefas até o pagamento ser confirmado. */
    public static Execucao planejar(String ordemServicoId, List<Tarefa> tarefas, Instant agora) {
        return new Execucao(ordemServicoId, StatusExecucao.PLANEJADA, tarefas, List.of(), null,
                agora, null, null, null, 0);
    }

    /**
     * Pagamento confirmado sem diagnóstico registrado aqui (evento perdido ou OS anterior a este
     * serviço): entra na fila sem tarefas, e o mecânico conclui a execução direto.
     */
    public static Execucao enfileirarSemPlano(String ordemServicoId, Instant agora) {
        return new Execucao(ordemServicoId, StatusExecucao.NA_FILA, List.of(), List.of(), null,
                agora, agora, null, null, 0);
    }

    public void enfileirar(Instant agora) {
        exigirStatus(StatusExecucao.PLANEJADA, "entrar na fila");
        status = StatusExecucao.NA_FILA;
        enfileiradaEm = agora;
    }

    public void iniciar(Instant agora) {
        exigirStatus(StatusExecucao.NA_FILA, "ser iniciada");
        status = StatusExecucao.EM_EXECUCAO;
        iniciadaEm = agora;
    }

    public void iniciarTarefa(String servicoId, Instant agora) {
        exigirStatus(StatusExecucao.EM_EXECUCAO, "ter tarefas iniciadas");
        substituirTarefa(servicoId, tarefa -> tarefa.iniciar(agora));
    }

    public void concluirTarefa(String servicoId, Instant agora) {
        exigirStatus(StatusExecucao.EM_EXECUCAO, "ter tarefas concluídas");
        substituirTarefa(servicoId, tarefa -> tarefa.concluir(agora));
    }

    public void registrarAndamento(EtapaExecucao etapa, String descricao, Instant agora) {
        exigirStatus(StatusExecucao.EM_EXECUCAO, "receber andamentos");
        andamentos.add(new Andamento(etapa, descricao, agora));
    }

    /** Fim do reparo: exige todas as tarefas concluídas. */
    public void concluir(Instant agora) {
        exigirStatus(StatusExecucao.EM_EXECUCAO, "ser concluída");
        List<String> pendentes = tarefas.stream()
                .filter(t -> t.status() != StatusTarefa.CONCLUIDA)
                .map(Tarefa::servicoId)
                .toList();
        if (!pendentes.isEmpty()) {
            throw new IllegalStateException("Ainda há tarefas não concluídas: " + String.join(", ", pendentes));
        }
        status = StatusExecucao.CONCLUIDA;
        finalizadaEm = agora;
    }

    /**
     * Reparo inviável. Só vale durante a execução: antes dela a OS ainda está em
     * {@code AGUARDANDO_EXECUCAO} e não aceitaria a falha, enquanto billing e inventory já
     * compensariam, deixando os serviços em desacordo.
     */
    public void falhar(String motivo, Instant agora) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("O motivo da falha é obrigatório");
        }
        exigirStatus(StatusExecucao.EM_EXECUCAO, "falhar");
        status = StatusExecucao.FALHOU;
        motivoFalha = motivo;
        finalizadaEm = agora;
    }

    private void exigirStatus(StatusExecucao esperado, String acao) {
        if (status != esperado) {
            throw new IllegalStateException(
                    "A execução da OS " + ordemServicoId + " está " + status + " e só pode " + acao + " em " + esperado);
        }
    }

    private void substituirTarefa(String servicoId, UnaryOperator<Tarefa> mudanca) {
        for (int i = 0; i < tarefas.size(); i++) {
            if (tarefas.get(i).servicoId().equals(servicoId)) {
                tarefas.set(i, mudanca.apply(tarefas.get(i)));
                return;
            }
        }
        throw new TarefaNaoEncontradaException(ordemServicoId, servicoId);
    }

    public String getOrdemServicoId() {
        return ordemServicoId;
    }

    public StatusExecucao getStatus() {
        return status;
    }

    public List<Tarefa> getTarefas() {
        return List.copyOf(tarefas);
    }

    public List<Andamento> getAndamentos() {
        return List.copyOf(andamentos);
    }

    public String getMotivoFalha() {
        return motivoFalha;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getEnfileiradaEm() {
        return enfileiradaEm;
    }

    public Instant getIniciadaEm() {
        return iniciadaEm;
    }

    public Instant getFinalizadaEm() {
        return finalizadaEm;
    }

    /** Versão lida do banco; 0 para uma execução que ainda não foi gravada. */
    public long getVersao() {
        return versao;
    }
}
