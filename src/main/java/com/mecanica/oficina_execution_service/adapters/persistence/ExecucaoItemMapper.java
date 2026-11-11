package com.mecanica.oficina_execution_service.adapters.persistence;

import static com.mecanica.oficina_execution_service.adapters.persistence.EsquemaDynamo.*;

import com.mecanica.oficina_execution_service.domain.execucao.Andamento;
import com.mecanica.oficina_execution_service.domain.execucao.EtapaExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusTarefa;
import com.mecanica.oficina_execution_service.domain.execucao.Tarefa;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Converte a execução no item do DynamoDB e de volta. Tarefas e andamentos ficam no próprio item. */
public final class ExecucaoItemMapper {

    private ExecucaoItemMapper() {
    }

    /** Item a gravar, já com a versão seguinte à lida. */
    public static Map<String, AttributeValue> paraItem(Execucao execucao) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(ORDEM_SERVICO_ID, s(execucao.getOrdemServicoId()));
        item.put(STATUS, s(execucao.getStatus().name()));
        Instant chegada = execucao.getEnfileiradaEm() != null ? execucao.getEnfileiradaEm() : execucao.getCriadaEm();
        item.put(ORDENACAO, s(formatar(chegada)));
        item.put(VERSAO, n(execucao.getVersao() + 1));
        item.put("criadaEm", s(execucao.getCriadaEm().toString()));
        colocarData(item, "enfileiradaEm", execucao.getEnfileiradaEm());
        colocarData(item, "iniciadaEm", execucao.getIniciadaEm());
        colocarData(item, "finalizadaEm", execucao.getFinalizadaEm());
        if (execucao.getMotivoFalha() != null) item.put("motivoFalha", s(execucao.getMotivoFalha()));
        item.put("tarefas", AttributeValue.fromL(execucao.getTarefas().stream().map(ExecucaoItemMapper::tarefa).toList()));
        item.put("andamentos", AttributeValue.fromL(execucao.getAndamentos().stream().map(ExecucaoItemMapper::andamento).toList()));
        return item;
    }

    public static Execucao paraExecucao(Map<String, AttributeValue> item) {
        return new Execucao(
                item.get(ORDEM_SERVICO_ID).s(),
                StatusExecucao.valueOf(item.get(STATUS).s()),
                lista(item, "tarefas").stream().map(v -> tarefa(v.m())).toList(),
                lista(item, "andamentos").stream().map(v -> andamento(v.m())).toList(),
                texto(item, "motivoFalha"),
                data(item, "criadaEm"),
                data(item, "enfileiradaEm"),
                data(item, "iniciadaEm"),
                data(item, "finalizadaEm"),
                Long.parseLong(item.get(VERSAO).n()));
    }

    private static AttributeValue tarefa(Tarefa tarefa) {
        Map<String, AttributeValue> m = new HashMap<>();
        m.put("servicoId", s(tarefa.servicoId()));
        if (tarefa.descricao() != null) m.put("descricao", s(tarefa.descricao()));
        m.put("quantidade", n(tarefa.quantidade()));
        m.put("status", s(tarefa.status().name()));
        colocarData(m, "iniciadaEm", tarefa.iniciadaEm());
        colocarData(m, "concluidaEm", tarefa.concluidaEm());
        return AttributeValue.fromM(m);
    }

    private static Tarefa tarefa(Map<String, AttributeValue> m) {
        return new Tarefa(
                m.get("servicoId").s(),
                texto(m, "descricao"),
                Integer.parseInt(m.get("quantidade").n()),
                StatusTarefa.valueOf(m.get("status").s()),
                data(m, "iniciadaEm"),
                data(m, "concluidaEm"));
    }

    private static AttributeValue andamento(Andamento andamento) {
        return AttributeValue.fromM(Map.of(
                "etapa", s(andamento.etapa().name()),
                "descricao", s(andamento.descricao()),
                "registradoEm", s(andamento.registradoEm().toString())));
    }

    private static Andamento andamento(Map<String, AttributeValue> m) {
        return new Andamento(EtapaExecucao.valueOf(m.get("etapa").s()), m.get("descricao").s(), data(m, "registradoEm"));
    }

    private static AttributeValue s(String valor) {
        return AttributeValue.fromS(valor);
    }

    private static AttributeValue n(long valor) {
        return AttributeValue.fromN(Long.toString(valor));
    }

    private static void colocarData(Map<String, AttributeValue> item, String nome, Instant valor) {
        if (valor != null) item.put(nome, s(valor.toString()));
    }

    private static String texto(Map<String, AttributeValue> item, String nome) {
        AttributeValue valor = item.get(nome);
        return valor == null ? null : valor.s();
    }

    private static Instant data(Map<String, AttributeValue> item, String nome) {
        String valor = texto(item, nome);
        return valor == null ? null : Instant.parse(valor);
    }

    private static List<AttributeValue> lista(Map<String, AttributeValue> item, String nome) {
        AttributeValue valor = item.get(nome);
        return valor == null || !valor.hasL() ? List.of() : valor.l();
    }
}
