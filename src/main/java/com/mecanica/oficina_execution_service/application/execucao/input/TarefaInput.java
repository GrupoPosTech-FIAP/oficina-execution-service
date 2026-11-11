package com.mecanica.oficina_execution_service.application.execucao.input;

/** Serviço do diagnóstico, como chega no {@code DiagnosticoConcluido}. */
public record TarefaInput(String servicoId, String descricao, int quantidade) {
}
