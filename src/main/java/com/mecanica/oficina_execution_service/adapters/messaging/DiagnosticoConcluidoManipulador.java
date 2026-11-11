package com.mecanica.oficina_execution_service.adapters.messaging;

import com.mecanica.oficina_execution_service.application.evento.EventosSaga;
import com.mecanica.oficina_execution_service.application.execucao.input.TarefaInput;
import com.mecanica.oficina_execution_service.application.execucao.usecase.PlanejarExecucaoUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** {@code DiagnosticoConcluido} → guarda os serviços do diagnóstico como tarefas da execução. */
@Component
public class DiagnosticoConcluidoManipulador implements ManipuladorEvento {

    private final PlanejarExecucaoUseCase useCase;

    public DiagnosticoConcluidoManipulador(PlanejarExecucaoUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(EventosSaga.DIAGNOSTICO_CONCLUIDO);
    }

    @Override
    public void tratar(EventoEnvelope evento) {
        List<TarefaInput> servicos = new ArrayList<>();
        JsonNode lista = evento.payload() == null ? null : evento.payload().get("servicos");
        if (lista != null) {
            for (JsonNode servico : lista) {
                servicos.add(new TarefaInput(
                        servico.path("servicoId").asString(null),
                        servico.path("descricao").asString(null),
                        servico.path("quantidade").asInt(1)));
            }
        }
        useCase.executar(evento.sagaId(), servicos);
    }
}
