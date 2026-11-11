package com.mecanica.oficina_execution_service.adapters.web.dto;

import java.util.List;

public record ErroResponse(String mensagem, List<CampoInvalido> campos) {

    public record CampoInvalido(String campo, String mensagem) {
    }

    public static ErroResponse de(String mensagem) {
        return new ErroResponse(mensagem, List.of());
    }
}
