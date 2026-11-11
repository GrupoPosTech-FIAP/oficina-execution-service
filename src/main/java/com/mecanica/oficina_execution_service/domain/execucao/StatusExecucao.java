package com.mecanica.oficina_execution_service.domain.execucao;

/**
 * Ciclo de vida da execução de uma OS neste serviço.
 *
 * <pre>
 * PLANEJADA ──PagamentoConfirmado──► NA_FILA ──iniciar──► EM_EXECUCAO ──concluir──► CONCLUIDA
 *                                                              └────────falhar────► FALHOU
 * </pre>
 *
 * {@code PLANEJADA} guarda as tarefas vindas do diagnóstico enquanto a OS ainda passa por orçamento
 * e pagamento; ela só entra na fila depois do pagamento confirmado.
 */
public enum StatusExecucao {
    PLANEJADA,
    NA_FILA,
    EM_EXECUCAO,
    CONCLUIDA,
    FALHOU
}
