package com.mecanica.oficina_execution_service.domain.execucao;

import java.time.Instant;
import java.util.Objects;

/** Anotação do mecânico sobre o andamento do diagnóstico ou do reparo. */
public record Andamento(EtapaExecucao etapa, String descricao, Instant registradoEm) {

    public Andamento {
        Objects.requireNonNull(etapa, "A etapa é obrigatória");
        Objects.requireNonNull(registradoEm, "A data do registro é obrigatória");
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("A descrição do andamento é obrigatória");
        }
    }
}
