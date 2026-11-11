package com.mecanica.oficina_execution_service.adapters.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mecanica.oficina_execution_service.DynamoTestcontainersSupport;
import com.mecanica.oficina_execution_service.application.evento.EventoSaga;
import com.mecanica.oficina_execution_service.application.execucao.gateway.ConflitoDeConcorrenciaException;
import com.mecanica.oficina_execution_service.domain.execucao.EtapaExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusTarefa;
import com.mecanica.oficina_execution_service.domain.execucao.Tarefa;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DynamoExecucaoGatewayTest extends DynamoTestcontainersSupport {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");

    @Autowired
    DynamoExecucaoGateway gateway;

    @Autowired
    DynamoOutboxRepository outbox;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    @DisplayName("Grava e lê a execução com tarefas, andamentos e datas")
    void idaEVolta() {
        Execucao execucao = Execucao.planejar("os-1", List.of(Tarefa.pendente("srv-1", "Troca de óleo", 2)), T0);
        gateway.salvar(execucao, List.of());

        Execucao lida = gateway.buscar("os-1").orElseThrow();
        assertThat(lida.getVersao()).isEqualTo(1);
        lida.enfileirar(T0.plusSeconds(60));
        lida.iniciar(T0.plusSeconds(120));
        lida.iniciarTarefa("srv-1", T0.plusSeconds(130));
        lida.registrarAndamento(EtapaExecucao.REPARO, "Óleo drenado", T0.plusSeconds(140));
        gateway.salvar(lida, List.of());

        Execucao relida = gateway.buscar("os-1").orElseThrow();
        assertThat(relida.getVersao()).isEqualTo(2);
        assertThat(relida.getStatus()).isEqualTo(StatusExecucao.EM_EXECUCAO);
        assertThat(relida.getCriadaEm()).isEqualTo(T0);
        assertThat(relida.getEnfileiradaEm()).isEqualTo(T0.plusSeconds(60));
        assertThat(relida.getIniciadaEm()).isEqualTo(T0.plusSeconds(120));
        assertThat(relida.getTarefas()).singleElement().satisfies(t -> {
            assertThat(t.descricao()).isEqualTo("Troca de óleo");
            assertThat(t.quantidade()).isEqualTo(2);
            assertThat(t.status()).isEqualTo(StatusTarefa.EM_ANDAMENTO);
            assertThat(t.iniciadaEm()).isEqualTo(T0.plusSeconds(130));
        });
        assertThat(relida.getAndamentos()).singleElement()
                .satisfies(a -> assertThat(a.descricao()).isEqualTo("Óleo drenado"));
    }

    @Test
    @DisplayName("OS sem execução")
    void inexistente() {
        assertThat(gateway.buscar("os-x")).isEmpty();
    }

    @Test
    @DisplayName("Segunda criação da mesma OS é recusada")
    void criacaoDuplicada() {
        gateway.salvar(Execucao.planejar("os-1", List.of(), T0), List.of());

        assertThatThrownBy(() -> gateway.salvar(Execucao.enfileirarSemPlano("os-1", T0), List.of()))
                .isInstanceOf(ConflitoDeConcorrenciaException.class);
        assertThat(gateway.buscar("os-1").orElseThrow().getStatus()).isEqualTo(StatusExecucao.PLANEJADA);
    }

    @Test
    @DisplayName("Gravação com versão desatualizada é recusada e não grava os eventos")
    void versaoDesatualizada() {
        gateway.salvar(Execucao.enfileirarSemPlano("os-1", T0), List.of());
        Execucao primeira = gateway.buscar("os-1").orElseThrow();
        Execucao segunda = gateway.buscar("os-1").orElseThrow();

        primeira.iniciar(T0);
        gateway.salvar(primeira, List.of(new EventoSaga("ExecucaoIniciada", "os-1", Map.of("ordemServicoId", "os-1"))));

        segunda.iniciar(T0);
        assertThatThrownBy(() -> gateway.salvar(segunda,
                List.of(new EventoSaga("ExecucaoIniciada", "os-1", Map.of("ordemServicoId", "os-1")))))
                .isInstanceOf(ConflitoDeConcorrenciaException.class);

        assertThat(itensDaOutbox()).hasSize(1);
    }

    @Test
    @DisplayName("Eventos vão para a outbox na mesma gravação, com o envelope completo")
    void eventosNaOutbox() {
        Execucao execucao = Execucao.enfileirarSemPlano("os-1", T0);
        execucao.iniciar(T0);
        gateway.salvar(execucao, List.of(new EventoSaga("ExecucaoIniciada", "os-1",
                Map.of("ordemServicoId", "os-1", "iniciadaEm", T0.toString()))));

        var pendentes = outbox.pendentes(10);
        assertThat(pendentes).singleElement().satisfies(p -> {
            assertThat(p.eventType()).isEqualTo("ExecucaoIniciada");
            assertThat(p.sagaId()).isEqualTo("os-1");
            assertThat(p.tentativas()).isZero();
            var envelope = jsonMapper.readTree(p.envelope());
            assertThat(envelope.get("eventId").asString()).isEqualTo(p.eventId());
            assertThat(envelope.get("producer").asString()).isEqualTo("oficina-execution-service");
            assertThat(envelope.get("payload").get("iniciadaEm").asString()).isEqualTo(T0.toString());
        });
    }

    @Test
    @DisplayName("Fila lista só o status pedido, em ordem de chegada")
    void filaEmOrdem() {
        gateway.salvar(Execucao.enfileirarSemPlano("os-c", T0.plusSeconds(30)), List.of());
        gateway.salvar(Execucao.enfileirarSemPlano("os-a", T0.plusMillis(5)), List.of());
        gateway.salvar(Execucao.enfileirarSemPlano("os-b", T0.plusSeconds(10)), List.of());
        gateway.salvar(Execucao.planejar("os-p", List.of(), T0), List.of());

        assertThat(gateway.listarPorStatus(StatusExecucao.NA_FILA))
                .extracting(Execucao::getOrdemServicoId)
                .containsExactly("os-a", "os-b", "os-c");
        assertThat(gateway.listarPorStatus(StatusExecucao.CONCLUIDA)).isEmpty();
    }

    @Test
    @DisplayName("Outbox: publicado sai dos pendentes; falha conta a tentativa")
    void outboxPublicacao() {
        Execucao execucao = Execucao.enfileirarSemPlano("os-1", T0);
        execucao.iniciar(T0);
        gateway.salvar(execucao, List.of(
                new EventoSaga("ExecucaoIniciada", "os-1", Map.of()),
                new EventoSaga("ExecucaoIniciada", "os-2", Map.of())));
        var pendentes = outbox.pendentes(10);
        assertThat(pendentes).hasSize(2);

        outbox.registrarFalha(pendentes.get(0).eventId(), "SNS fora");
        outbox.marcarPublicado(pendentes.get(1).eventId(), T0);

        assertThat(outbox.pendentes(10)).singleElement().satisfies(p -> {
            assertThat(p.eventId()).isEqualTo(pendentes.get(0).eventId());
            assertThat(p.tentativas()).isEqualTo(1);
        });
    }
}
