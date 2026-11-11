package com.mecanica.oficina_execution_service.adapters.web;

import com.mecanica.oficina_execution_service.adapters.web.dto.ErroResponse;
import com.mecanica.oficina_execution_service.application.execucao.ExecucaoNaoEncontradaException;
import com.mecanica.oficina_execution_service.application.execucao.gateway.ConflitoDeConcorrenciaException;
import com.mecanica.oficina_execution_service.domain.execucao.TarefaNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ExecucaoNaoEncontradaException.class, TarefaNaoEncontradaException.class})
    public ResponseEntity<ErroResponse> naoEncontrado(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroResponse.de(e.getMessage()));
    }

    /** Transição inválida para o status atual, ou gravação concorrente: o cliente pode reler e tentar de novo. */
    @ExceptionHandler({IllegalStateException.class, ConflitoDeConcorrenciaException.class})
    public ResponseEntity<ErroResponse> conflito(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErroResponse.de(e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErroResponse> requisicaoInvalida(RuntimeException e) {
        return ResponseEntity.badRequest().body(ErroResponse.de(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException e) {
        var campos = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ErroResponse.CampoInvalido(erro.getField(), erro.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ErroResponse("Erro de validação nos campos", campos));
    }
}
