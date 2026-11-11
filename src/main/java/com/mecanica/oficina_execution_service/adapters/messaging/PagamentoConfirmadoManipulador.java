package com.mecanica.oficina_execution_service.adapters.messaging;

import com.mecanica.oficina_execution_service.application.evento.EventosSaga;
import com.mecanica.oficina_execution_service.application.execucao.usecase.EnfileirarExecucaoUseCase;
import java.util.Set;
import org.springframework.stereotype.Component;

/** {@code PagamentoConfirmado} → coloca a OS na fila de execução. */
@Component
public class PagamentoConfirmadoManipulador implements ManipuladorEvento {

    private final EnfileirarExecucaoUseCase useCase;

    public PagamentoConfirmadoManipulador(EnfileirarExecucaoUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(EventosSaga.PAGAMENTO_CONFIRMADO);
    }

    @Override
    public void tratar(EventoEnvelope evento) {
        useCase.executar(evento.sagaId());
    }
}
