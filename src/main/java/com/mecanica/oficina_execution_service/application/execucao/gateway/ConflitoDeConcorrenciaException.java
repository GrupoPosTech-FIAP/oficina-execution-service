package com.mecanica.oficina_execution_service.application.execucao.gateway;

/** A execução foi gravada por outra requisição ou evento entre a leitura e a gravação. */
public class ConflitoDeConcorrenciaException extends RuntimeException {

    public ConflitoDeConcorrenciaException(String ordemServicoId, Throwable causa) {
        super("A execução da OS " + ordemServicoId + " foi alterada ao mesmo tempo; tente de novo", causa);
    }
}
