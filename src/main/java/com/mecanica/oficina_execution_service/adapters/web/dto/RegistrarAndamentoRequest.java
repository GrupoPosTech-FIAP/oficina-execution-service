package com.mecanica.oficina_execution_service.adapters.web.dto;

import com.mecanica.oficina_execution_service.domain.execucao.EtapaExecucao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistrarAndamentoRequest(
        @NotNull(message = "A etapa é obrigatória") EtapaExecucao etapa,
        @NotBlank(message = "A descrição é obrigatória") @Size(max = 1000) String descricao) {
}
