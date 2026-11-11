package com.mecanica.oficina_execution_service.domain.execucao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecucaoTest {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-10T11:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-10T12:00:00Z");

    private static Execucao emExecucao(Tarefa... tarefas) {
        Execucao execucao = Execucao.planejar("os-1", List.of(tarefas), T0);
        execucao.enfileirar(T0);
        execucao.iniciar(T1);
        return execucao;
    }

    @Test
    @DisplayName("Fluxo completo: planejada → fila → execução → concluída")
    void fluxoCompleto() {
        Execucao execucao = Execucao.planejar("os-1", List.of(Tarefa.pendente("srv-1", "Troca de óleo", 1)), T0);
        assertThat(execucao.getStatus()).isEqualTo(StatusExecucao.PLANEJADA);
        assertThat(execucao.getVersao()).isZero();

        execucao.enfileirar(T0);
        assertThat(execucao.getStatus()).isEqualTo(StatusExecucao.NA_FILA);
        assertThat(execucao.getEnfileiradaEm()).isEqualTo(T0);

        execucao.iniciar(T1);
        execucao.iniciarTarefa("srv-1", T1);
        execucao.registrarAndamento(EtapaExecucao.REPARO, "Filtro trocado", T1);
        execucao.concluirTarefa("srv-1", T2);
        execucao.concluir(T2);

        assertThat(execucao.getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        assertThat(execucao.getIniciadaEm()).isEqualTo(T1);
        assertThat(execucao.getFinalizadaEm()).isEqualTo(T2);
        assertThat(execucao.getTarefas()).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(StatusTarefa.CONCLUIDA);
            assertThat(t.iniciadaEm()).isEqualTo(T1);
            assertThat(t.concluidaEm()).isEqualTo(T2);
        });
        assertThat(execucao.getAndamentos()).extracting(Andamento::descricao).containsExactly("Filtro trocado");
    }

    @Test
    @DisplayName("Pagamento sem diagnóstico entra na fila sem tarefas e pode ser concluído direto")
    void semPlano() {
        Execucao execucao = Execucao.enfileirarSemPlano("os-1", T0);
        assertThat(execucao.getStatus()).isEqualTo(StatusExecucao.NA_FILA);
        assertThat(execucao.getTarefas()).isEmpty();

        execucao.iniciar(T1);
        execucao.concluir(T2);
        assertThat(execucao.getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
    }

    @Test
    @DisplayName("Não conclui com tarefa pendente")
    void naoConcluiComTarefaPendente() {
        Execucao execucao = emExecucao(Tarefa.pendente("srv-1", "a", 1), Tarefa.pendente("srv-2", "b", 1));
        execucao.concluirTarefa("srv-1", T2);

        assertThatThrownBy(() -> execucao.concluir(T2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("srv-2");
        assertThat(execucao.getStatus()).isEqualTo(StatusExecucao.EM_EXECUCAO);
    }

    @Test
    @DisplayName("Concluir tarefa ainda pendente registra início e fim no mesmo instante")
    void concluirTarefaPendente() {
        Execucao execucao = emExecucao(Tarefa.pendente("srv-1", "a", 1));
        execucao.concluirTarefa("srv-1", T2);

        assertThat(execucao.getTarefas().getFirst().iniciadaEm()).isEqualTo(T2);
    }

    @Test
    @DisplayName("Tarefa não pode ser iniciada duas vezes nem concluída duas vezes")
    void tarefaNaoRepete() {
        Execucao execucao = emExecucao(Tarefa.pendente("srv-1", "a", 1));
        execucao.iniciarTarefa("srv-1", T1);

        assertThatThrownBy(() -> execucao.iniciarTarefa("srv-1", T1)).isInstanceOf(IllegalStateException.class);
        execucao.concluirTarefa("srv-1", T2);
        assertThatThrownBy(() -> execucao.concluirTarefa("srv-1", T2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Tarefa de serviço que não está na execução")
    void tarefaInexistente() {
        Execucao execucao = emExecucao(Tarefa.pendente("srv-1", "a", 1));

        assertThatThrownBy(() -> execucao.iniciarTarefa("srv-9", T1))
                .isInstanceOf(TarefaNaoEncontradaException.class)
                .hasMessageContaining("srv-9");
    }

    @Test
    @DisplayName("Falha registra o motivo e só vale durante a execução")
    void falha() {
        Execucao naFila = Execucao.enfileirarSemPlano("os-1", T0);
        assertThatThrownBy(() -> naFila.falhar("motor fundido", T1)).isInstanceOf(IllegalStateException.class);

        Execucao execucao = emExecucao();
        assertThatThrownBy(() -> execucao.falhar(" ", T2)).isInstanceOf(IllegalArgumentException.class);

        execucao.falhar("motor fundido", T2);
        assertThat(execucao.getStatus()).isEqualTo(StatusExecucao.FALHOU);
        assertThat(execucao.getMotivoFalha()).isEqualTo("motor fundido");
        assertThat(execucao.getFinalizadaEm()).isEqualTo(T2);
    }

    @Test
    @DisplayName("Transições fora de ordem são recusadas")
    void transicoesForaDeOrdem() {
        Execucao planejada = Execucao.planejar("os-1", List.of(), T0);
        assertThatThrownBy(() -> planejada.iniciar(T1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> planejada.concluir(T1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> planejada.registrarAndamento(EtapaExecucao.TESTE, "x", T1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> planejada.iniciarTarefa("srv-1", T1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> planejada.concluirTarefa("srv-1", T1)).isInstanceOf(IllegalStateException.class);

        Execucao naFila = Execucao.enfileirarSemPlano("os-1", T0);
        assertThatThrownBy(() -> naFila.enfileirar(T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Validações de criação")
    void validacoes() {
        assertThatThrownBy(() -> Execucao.planejar(" ", List.of(), T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tarefa.pendente("", "a", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tarefa.pendente("srv-1", "a", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Andamento(EtapaExecucao.REPARO, " ", T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Listas expostas são cópias")
    void listasImutaveis() {
        Execucao execucao = Execucao.planejar("os-1", List.of(Tarefa.pendente("srv-1", "a", 1)), T0);

        assertThatThrownBy(() -> execucao.getTarefas().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> execucao.getAndamentos().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
