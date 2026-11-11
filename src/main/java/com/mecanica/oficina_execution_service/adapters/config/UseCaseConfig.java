package com.mecanica.oficina_execution_service.adapters.config;

import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.application.execucao.usecase.AtualizarTarefaUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.ConcluirExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.ConsultarExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.ConsultarTempoMedioServicoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.EnfileirarExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.FalharExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.IniciarExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.PlanejarExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.RegistrarAndamentoUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Casos de uso não conhecem o Spring: são montados aqui. */
@Configuration
public class UseCaseConfig {

    @Bean
    PlanejarExecucaoUseCase planejarExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        return new PlanejarExecucaoUseCase(gateway, clock);
    }

    @Bean
    EnfileirarExecucaoUseCase enfileirarExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        return new EnfileirarExecucaoUseCase(gateway, clock);
    }

    @Bean
    IniciarExecucaoUseCase iniciarExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        return new IniciarExecucaoUseCase(gateway, clock);
    }

    @Bean
    AtualizarTarefaUseCase atualizarTarefaUseCase(ExecucaoGateway gateway, Clock clock) {
        return new AtualizarTarefaUseCase(gateway, clock);
    }

    @Bean
    RegistrarAndamentoUseCase registrarAndamentoUseCase(ExecucaoGateway gateway, Clock clock) {
        return new RegistrarAndamentoUseCase(gateway, clock);
    }

    @Bean
    ConcluirExecucaoUseCase concluirExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        return new ConcluirExecucaoUseCase(gateway, clock);
    }

    @Bean
    FalharExecucaoUseCase falharExecucaoUseCase(ExecucaoGateway gateway, Clock clock) {
        return new FalharExecucaoUseCase(gateway, clock);
    }

    @Bean
    ConsultarExecucaoUseCase consultarExecucaoUseCase(ExecucaoGateway gateway) {
        return new ConsultarExecucaoUseCase(gateway);
    }

    @Bean
    ConsultarTempoMedioServicoUseCase consultarTempoMedioServicoUseCase(ExecucaoGateway gateway) {
        return new ConsultarTempoMedioServicoUseCase(gateway);
    }
}
