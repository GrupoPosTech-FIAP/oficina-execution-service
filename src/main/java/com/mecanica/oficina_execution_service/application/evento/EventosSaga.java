package com.mecanica.oficina_execution_service.application.evento;

/** Nomes dos eventos da Saga (catálogo da ADR 005 em oficina-app-api) usados por este serviço. */
public final class EventosSaga {

    // Consumidos
    public static final String DIAGNOSTICO_CONCLUIDO = "DiagnosticoConcluido";
    public static final String PAGAMENTO_CONFIRMADO = "PagamentoConfirmado";

    // Publicados
    public static final String EXECUCAO_INICIADA = "ExecucaoIniciada";
    public static final String EXECUCAO_CONCLUIDA = "ExecucaoConcluida";
    public static final String EXECUCAO_FALHOU = "ExecucaoFalhou";

    private EventosSaga() {
    }
}
