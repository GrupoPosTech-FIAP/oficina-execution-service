package com.mecanica.oficina_execution_service.application.execucao.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mecanica.oficina_execution_service.application.evento.EventoSaga;
import com.mecanica.oficina_execution_service.application.execucao.ExecucaoNaoEncontradaException;
import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.application.execucao.input.TarefaInput;
import com.mecanica.oficina_execution_service.domain.execucao.EtapaExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusTarefa;
import com.mecanica.oficina_execution_service.domain.execucao.Tarefa;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CasosDeUsoExecucaoTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Mock
    ExecucaoGateway gateway;

    @Captor
    ArgumentCaptor<Execucao> execucaoSalva;

    @Captor
    ArgumentCaptor<List<EventoSaga>> eventosSalvos;

    private Execucao emExecucao(Tarefa... tarefas) {
        Execucao execucao = Execucao.planejar("os-1", List.of(tarefas), AGORA);
        execucao.enfileirar(AGORA);
        execucao.iniciar(AGORA);
        return execucao;
    }

    private void existe(Execucao execucao) {
        when(gateway.encontrarOuLancar("os-1")).thenReturn(execucao);
    }

    @Nested
    class Planejar {

        @Test
        @DisplayName("DiagnosticoConcluido cria a execução PLANEJADA com as tarefas")
        void criaPlanejada() {
            when(gateway.buscar("os-1")).thenReturn(Optional.empty());

            var resultado = new PlanejarExecucaoUseCase(gateway, CLOCK)
                    .executar("os-1", List.of(new TarefaInput("srv-1", "Troca de óleo", 2)));

            assertThat(resultado).isEqualTo(PlanejarExecucaoUseCase.Resultado.PLANEJADA);
            verify(gateway).salvar(execucaoSalva.capture(), eq(List.of()));
            assertThat(execucaoSalva.getValue().getStatus()).isEqualTo(StatusExecucao.PLANEJADA);
            assertThat(execucaoSalva.getValue().getTarefas()).singleElement()
                    .satisfies(t -> assertThat(t.quantidade()).isEqualTo(2));
        }

        @Test
        @DisplayName("Evento repetido não mexe na execução existente")
        void repetido() {
            when(gateway.buscar("os-1")).thenReturn(Optional.of(Execucao.enfileirarSemPlano("os-1", AGORA)));

            var resultado = new PlanejarExecucaoUseCase(gateway, CLOCK).executar("os-1", List.of());

            assertThat(resultado).isEqualTo(PlanejarExecucaoUseCase.Resultado.JA_EXISTENTE);
            verify(gateway, never()).salvar(any(), anyList());
        }
    }

    @Nested
    class Enfileirar {

        @Test
        @DisplayName("PagamentoConfirmado põe a execução planejada na fila")
        void planejadaVaiParaFila() {
            when(gateway.buscar("os-1")).thenReturn(Optional.of(Execucao.planejar("os-1", List.of(), AGORA)));

            var resultado = new EnfileirarExecucaoUseCase(gateway, CLOCK).executar("os-1");

            assertThat(resultado).isEqualTo(EnfileirarExecucaoUseCase.Resultado.ENFILEIRADA);
            verify(gateway).salvar(argThat(e -> e.getStatus() == StatusExecucao.NA_FILA), eq(List.of()));
        }

        @Test
        @DisplayName("Sem diagnóstico registrado, cria a execução direto na fila")
        void semPlano() {
            when(gateway.buscar("os-1")).thenReturn(Optional.empty());

            var resultado = new EnfileirarExecucaoUseCase(gateway, CLOCK).executar("os-1");

            assertThat(resultado).isEqualTo(EnfileirarExecucaoUseCase.Resultado.ENFILEIRADA_SEM_PLANO);
            verify(gateway).salvar(argThat(e -> e.getStatus() == StatusExecucao.NA_FILA && e.getVersao() == 0), eq(List.of()));
        }

        @Test
        @DisplayName("Pagamento repetido é ignorado")
        void repetido() {
            when(gateway.buscar("os-1")).thenReturn(Optional.of(Execucao.enfileirarSemPlano("os-1", AGORA)));

            var resultado = new EnfileirarExecucaoUseCase(gateway, CLOCK).executar("os-1");

            assertThat(resultado).isEqualTo(EnfileirarExecucaoUseCase.Resultado.JA_ENFILEIRADA);
            verify(gateway, never()).salvar(any(), anyList());
        }
    }

    @Test
    @DisplayName("Iniciar publica ExecucaoIniciada junto com a gravação")
    void iniciar() {
        existe(Execucao.enfileirarSemPlano("os-1", AGORA));

        Execucao iniciada = new IniciarExecucaoUseCase(gateway, CLOCK).executar("os-1");

        assertThat(iniciada.getStatus()).isEqualTo(StatusExecucao.EM_EXECUCAO);
        verify(gateway).salvar(eq(iniciada), eventosSalvos.capture());
        assertThat(eventosSalvos.getValue()).containsExactly(new EventoSaga("ExecucaoIniciada", "os-1",
                Map.of("ordemServicoId", "os-1", "iniciadaEm", AGORA.toString())));
    }

    @Test
    @DisplayName("Concluir publica ExecucaoConcluida")
    void concluir() {
        existe(emExecucao());

        new ConcluirExecucaoUseCase(gateway, CLOCK).executar("os-1");

        verify(gateway).salvar(argThat(e -> e.getStatus() == StatusExecucao.CONCLUIDA), eventosSalvos.capture());
        assertThat(eventosSalvos.getValue()).containsExactly(new EventoSaga("ExecucaoConcluida", "os-1",
                Map.of("ordemServicoId", "os-1", "concluidaEm", AGORA.toString())));
    }

    @Test
    @DisplayName("Falhar publica ExecucaoFalhou com o motivo")
    void falhar() {
        existe(emExecucao());

        new FalharExecucaoUseCase(gateway, CLOCK).executar("os-1", "peça indisponível");

        verify(gateway).salvar(argThat(e -> e.getStatus() == StatusExecucao.FALHOU), eventosSalvos.capture());
        assertThat(eventosSalvos.getValue()).containsExactly(new EventoSaga("ExecucaoFalhou", "os-1",
                Map.of("ordemServicoId", "os-1", "motivo", "peça indisponível", "falhouEm", AGORA.toString())));
    }

    @Test
    @DisplayName("Transição inválida não grava nem publica")
    void transicaoInvalidaNaoGrava() {
        existe(Execucao.enfileirarSemPlano("os-1", AGORA));

        assertThatThrownBy(() -> new ConcluirExecucaoUseCase(gateway, CLOCK).executar("os-1"))
                .isInstanceOf(IllegalStateException.class);
        verify(gateway, never()).salvar(any(), anyList());
    }

    @Test
    @DisplayName("Tarefas e andamentos são gravados sem evento")
    void tarefasEAndamentos() {
        existe(emExecucao(Tarefa.pendente("srv-1", "a", 1)));
        var tarefas = new AtualizarTarefaUseCase(gateway, CLOCK);

        assertThat(tarefas.iniciar("os-1", "srv-1").getTarefas().getFirst().status()).isEqualTo(StatusTarefa.EM_ANDAMENTO);
        assertThat(tarefas.concluir("os-1", "srv-1").getTarefas().getFirst().status()).isEqualTo(StatusTarefa.CONCLUIDA);
        Execucao comAndamento = new RegistrarAndamentoUseCase(gateway, CLOCK)
                .executar("os-1", EtapaExecucao.DIAGNOSTICO, "Barulho na suspensão");

        assertThat(comAndamento.getAndamentos()).hasSize(1);
        verify(gateway, times(3)).salvar(any(), eq(List.of()));
    }

    @Test
    @DisplayName("Consulta e fila")
    void consultar() {
        Execucao execucao = Execucao.enfileirarSemPlano("os-1", AGORA);
        when(gateway.encontrarOuLancar("os-1")).thenReturn(execucao);
        when(gateway.listarPorStatus(StatusExecucao.NA_FILA)).thenReturn(List.of(execucao));
        var consultar = new ConsultarExecucaoUseCase(gateway);

        assertThat(consultar.buscar("os-1")).isSameAs(execucao);
        assertThat(consultar.listar(StatusExecucao.NA_FILA)).containsExactly(execucao);
    }

    @Test
    @DisplayName("Tempo médio considera só as tarefas concluídas do serviço nas execuções concluídas")
    void tempoMedio() {
        Instant inicio = AGORA.minusSeconds(3600);
        Execucao a = new Execucao("os-a", StatusExecucao.CONCLUIDA, List.of(
                new Tarefa("srv-1", "Troca de óleo", 1, StatusTarefa.CONCLUIDA, inicio, inicio.plusSeconds(30 * 60)),
                new Tarefa("srv-2", "Alinhamento", 1, StatusTarefa.CONCLUIDA, inicio, inicio.plusSeconds(5 * 60))),
                List.of(), null, inicio, inicio, inicio, AGORA, 3);
        Execucao b = new Execucao("os-b", StatusExecucao.CONCLUIDA, List.of(
                new Tarefa("srv-1", "Troca de óleo", 1, StatusTarefa.CONCLUIDA, inicio, inicio.plusSeconds(45 * 60))),
                List.of(), null, inicio, inicio, inicio, AGORA, 3);
        when(gateway.listarPorStatus(StatusExecucao.CONCLUIDA)).thenReturn(List.of(a, b));
        var useCase = new ConsultarTempoMedioServicoUseCase(gateway);

        assertThat(useCase.executar("srv-1"))
                .isEqualTo(new ConsultarTempoMedioServicoUseCase.TempoMedioServico("srv-1", "Troca de óleo", 2, 37.5));
        assertThat(useCase.executar("srv-9"))
                .isEqualTo(new ConsultarTempoMedioServicoUseCase.TempoMedioServico("srv-9", null, 0, 0.0));
    }

    @Test
    @DisplayName("encontrarOuLancar lança quando a OS não tem execução")
    void naoEncontrada() {
        ExecucaoGateway vazio = new ExecucaoGateway() {
            public Optional<Execucao> buscar(String id) { return Optional.empty(); }
            public List<Execucao> listarPorStatus(StatusExecucao status) { return List.of(); }
            public void salvar(Execucao execucao, List<EventoSaga> eventos) { }
        };

        assertThatThrownBy(() -> new IniciarExecucaoUseCase(vazio, CLOCK).executar("os-9"))
                .isInstanceOf(ExecucaoNaoEncontradaException.class)
                .hasMessageContaining("os-9");
    }
}
