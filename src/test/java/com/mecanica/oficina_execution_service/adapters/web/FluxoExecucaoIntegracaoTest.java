package com.mecanica.oficina_execution_service.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mecanica.oficina_execution_service.DynamoTestcontainersSupport;
import com.mecanica.oficina_execution_service.adapters.messaging.EventoEnvelope;
import com.mecanica.oficina_execution_service.adapters.messaging.ProcessadorEventos;
import com.mecanica.oficina_execution_service.adapters.persistence.DynamoOutboxRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fluxo da Saga neste serviço: eventos recebidos pela fila (entregues direto ao processador),
 * operações do mecânico pela API e eventos gravados na outbox para a OS, o billing e o inventory.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FluxoExecucaoIntegracaoTest extends DynamoTestcontainersSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProcessadorEventos processador;

    @Autowired
    DynamoOutboxRepository outbox;

    @Autowired
    JsonMapper json;

    @Value("${jwt.secret}")
    String segredo;

    private void receber(String eventType, String payload) {
        processador.processar(new EventoEnvelope(UUID.randomUUID().toString(), eventType, 1, Instant.now(),
                "oficina-app-api", "os-1", json.readTree(payload)));
    }

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder requisicao) {
        String token = Jwts.builder()
                .subject("mecanico@oficina.com")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(segredo)))
                .compact();
        return requisicao.header("Authorization", "Bearer " + token);
    }

    private void diagnosticoEPagamento() {
        receber("DiagnosticoConcluido", """
                {"ordemServicoId":"os-1","servicos":[
                  {"servicoId":"srv-1","descricao":"Troca de óleo","quantidade":1,"precoUnitario":120.0},
                  {"servicoId":"srv-2","descricao":"Alinhamento","quantidade":1,"precoUnitario":80.0}],
                 "insumos":[]}""");
        receber("PagamentoConfirmado", "{\"ordemServicoId\":\"os-1\",\"valor\":200.0}");
    }

    private void iniciar() throws Exception {
        mvc.perform(autenticado(patch("/execucoes/os-1/iniciar"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Caminho feliz: diagnóstico, pagamento, fila, execução e conclusão publicam os eventos para a OS")
    void caminhoFeliz() throws Exception {
        diagnosticoEPagamento();

        mvc.perform(autenticado(get("/execucoes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ordemServicoId").value("os-1"))
                .andExpect(jsonPath("$[0].status").value("NA_FILA"))
                .andExpect(jsonPath("$[0].tarefas.length()").value(2));

        iniciar();
        mvc.perform(autenticado(patch("/execucoes/os-1/tarefas/srv-1/iniciar")))
                .andExpect(jsonPath("$.tarefas[0].status").value("EM_ANDAMENTO"));
        mvc.perform(autenticado(post("/execucoes/os-1/andamentos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etapa\":\"REPARO\",\"descricao\":\"Óleo e filtro trocados\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.andamentos[0].etapa").value("REPARO"));
        mvc.perform(autenticado(patch("/execucoes/os-1/tarefas/srv-1/concluir"))).andExpect(status().isOk());

        // srv-2 ainda pendente
        mvc.perform(autenticado(patch("/execucoes/os-1/concluir")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("srv-2")));

        mvc.perform(autenticado(patch("/execucoes/os-1/tarefas/srv-2/concluir"))).andExpect(status().isOk());
        mvc.perform(autenticado(patch("/execucoes/os-1/concluir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));

        assertThat(outbox.pendentes(10))
                .extracting(DynamoOutboxRepository.EventoPendente::eventType)
                .containsExactly("ExecucaoIniciada", "ExecucaoConcluida");

        mvc.perform(autenticado(get("/execucoes/servicos/srv-1/tempo-medio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Troca de óleo"))
                .andExpect(jsonPath("$.execucoes").value(1));
    }

    @Test
    @DisplayName("Falha na execução publica ExecucaoFalhou, que dispara as compensações")
    void falha() throws Exception {
        diagnosticoEPagamento();
        iniciar();

        mvc.perform(autenticado(patch("/execucoes/os-1/falhar"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Bloco do motor trincado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALHOU"))
                .andExpect(jsonPath("$.motivoFalha").value("Bloco do motor trincado"));

        var eventos = outbox.pendentes(10);
        assertThat(eventos).extracting(DynamoOutboxRepository.EventoPendente::eventType)
                .containsExactly("ExecucaoIniciada", "ExecucaoFalhou");
        assertThat(json.readTree(eventos.get(1).envelope()).get("payload").get("motivo").asString())
                .isEqualTo("Bloco do motor trincado");
    }

    @Test
    @DisplayName("Eventos repetidos não duplicam nem regridem a execução")
    void eventosRepetidos() throws Exception {
        diagnosticoEPagamento();
        iniciar();
        diagnosticoEPagamento();

        mvc.perform(autenticado(get("/execucoes/os-1")))
                .andExpect(jsonPath("$.status").value("EM_EXECUCAO"));
        assertThat(outbox.pendentes(10)).hasSize(1);
    }

    @Test
    @DisplayName("Evento que este serviço não trata é ignorado")
    void eventoSemManipulador() {
        assertThat(processador.processar(new EventoEnvelope("ev-x", "OrcamentoGerado", 1, Instant.now(),
                "oficina-billing-service", "os-1", json.readTree("{}"))))
                .isEqualTo(ProcessadorEventos.Resultado.IGNORADO);
    }

    @Test
    @DisplayName("Erros da API: sem token, OS desconhecida, transição inválida e validação")
    void erros() throws Exception {
        mvc.perform(get("/execucoes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/execucoes").header("Authorization", "Bearer token-invalido")).andExpect(status().isUnauthorized());

        mvc.perform(autenticado(get("/execucoes/os-9"))).andExpect(status().isNotFound());
        mvc.perform(autenticado(get("/execucoes").param("status", "QUALQUER"))).andExpect(status().isBadRequest());

        receber("PagamentoConfirmado", "{\"ordemServicoId\":\"os-1\"}");
        mvc.perform(autenticado(patch("/execucoes/os-1/concluir"))).andExpect(status().isConflict());
        mvc.perform(autenticado(patch("/execucoes/os-1/tarefas/srv-1/iniciar"))).andExpect(status().isConflict());

        iniciar();
        mvc.perform(autenticado(patch("/execucoes/os-1/tarefas/srv-9/iniciar"))).andExpect(status().isNotFound());
        mvc.perform(autenticado(patch("/execucoes/os-1/falhar"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("motivo"));
        mvc.perform(autenticado(post("/execucoes/os-1/andamentos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"sem etapa\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Swagger e health ficam abertos")
    void rotasPublicas() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
