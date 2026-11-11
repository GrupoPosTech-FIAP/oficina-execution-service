package com.mecanica.oficina_execution_service.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FalharExecucaoRequest(
        @NotBlank(message = "O motivo da falha é obrigatório") @Size(max = 500) String motivo) {
}
