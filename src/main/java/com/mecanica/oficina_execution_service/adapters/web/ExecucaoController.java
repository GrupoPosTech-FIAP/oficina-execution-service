package com.mecanica.oficina_execution_service.adapters.web;

import com.mecanica.oficina_execution_service.adapters.web.dto.ExecucaoResponse;
import com.mecanica.oficina_execution_service.adapters.web.dto.FalharExecucaoRequest;
import com.mecanica.oficina_execution_service.adapters.web.dto.RegistrarAndamentoRequest;
import com.mecanica.oficina_execution_service.application.execucao.usecase.AtualizarTarefaUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.ConcluirExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.ConsultarExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.ConsultarTempoMedioServicoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.ConsultarTempoMedioServicoUseCase.TempoMedioServico;
import com.mecanica.oficina_execution_service.application.execucao.usecase.FalharExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.IniciarExecucaoUseCase;
import com.mecanica.oficina_execution_service.application.execucao.usecase.RegistrarAndamentoUseCase;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/execucoes")
@Tag(name = "Execução", description = "Fila de execução das OS e acompanhamento do reparo")
public class ExecucaoController {

    private final ConsultarExecucaoUseCase consultar;
    private final IniciarExecucaoUseCase iniciar;
    private final AtualizarTarefaUseCase tarefas;
    private final RegistrarAndamentoUseCase andamentos;
    private final ConcluirExecucaoUseCase concluir;
    private final FalharExecucaoUseCase falhar;
    private final ConsultarTempoMedioServicoUseCase tempoMedio;

    public ExecucaoController(ConsultarExecucaoUseCase consultar, IniciarExecucaoUseCase iniciar,
                              AtualizarTarefaUseCase tarefas, RegistrarAndamentoUseCase andamentos,
                              ConcluirExecucaoUseCase concluir, FalharExecucaoUseCase falhar,
                              ConsultarTempoMedioServicoUseCase tempoMedio) {
        this.tempoMedio = tempoMedio;
        this.consultar = consultar;
        this.iniciar = iniciar;
        this.tarefas = tarefas;
        this.andamentos = andamentos;
        this.concluir = concluir;
        this.falhar = falhar;
    }

    @GetMapping
    @Operation(summary = "Fila de execução",
            description = "Execuções no status informado (padrão NA_FILA), das mais antigas para as mais novas.")
    public List<ExecucaoResponse> listar(@RequestParam(defaultValue = "NA_FILA") StatusExecucao status) {
        return consultar.listar(status).stream().map(ExecucaoPresenter::apresentar).toList();
    }

    @GetMapping("/servicos/{servicoId}/tempo-medio")
    @Operation(summary = "Tempo médio de um serviço",
            description = "Média, em minutos, do início ao fim das tarefas desse serviço nas execuções concluídas.")
    public TempoMedioServico tempoMedio(@PathVariable String servicoId) {
        return tempoMedio.executar(servicoId);
    }

    @GetMapping("/{ordemServicoId}")
    @Operation(summary = "Execução de uma OS", description = "Status, tarefas e andamentos registrados.")
    public ExecucaoResponse buscar(@PathVariable String ordemServicoId) {
        return ExecucaoPresenter.apresentar(consultar.buscar(ordemServicoId));
    }

    @PatchMapping("/{ordemServicoId}/iniciar")
    @Operation(summary = "Iniciar execução",
            description = "NA_FILA → EM_EXECUCAO. Publica ExecucaoIniciada e a OS vai para EM_EXECUCAO.")
    public ExecucaoResponse iniciar(@PathVariable String ordemServicoId) {
        return ExecucaoPresenter.apresentar(iniciar.executar(ordemServicoId));
    }

    @PatchMapping("/{ordemServicoId}/tarefas/{servicoId}/iniciar")
    @Operation(summary = "Iniciar um serviço da OS", description = "Tarefa PENDENTE → EM_ANDAMENTO.")
    public ExecucaoResponse iniciarTarefa(@PathVariable String ordemServicoId, @PathVariable String servicoId) {
        return ExecucaoPresenter.apresentar(tarefas.iniciar(ordemServicoId, servicoId));
    }

    @PatchMapping("/{ordemServicoId}/tarefas/{servicoId}/concluir")
    @Operation(summary = "Concluir um serviço da OS", description = "Tarefa → CONCLUIDA.")
    public ExecucaoResponse concluirTarefa(@PathVariable String ordemServicoId, @PathVariable String servicoId) {
        return ExecucaoPresenter.apresentar(tarefas.concluir(ordemServicoId, servicoId));
    }

    @PostMapping("/{ordemServicoId}/andamentos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar andamento", description = "Anotação sobre o diagnóstico, o reparo ou o teste.")
    public ExecucaoResponse registrarAndamento(@PathVariable String ordemServicoId,
                                               @Valid @RequestBody RegistrarAndamentoRequest request) {
        return ExecucaoPresenter.apresentar(andamentos.executar(ordemServicoId, request.etapa(), request.descricao()));
    }

    @PatchMapping("/{ordemServicoId}/concluir")
    @Operation(summary = "Concluir execução",
            description = "EM_EXECUCAO → CONCLUIDA, com todas as tarefas concluídas. Publica ExecucaoConcluida e a OS vai para FINALIZADA.")
    public ExecucaoResponse concluir(@PathVariable String ordemServicoId) {
        return ExecucaoPresenter.apresentar(concluir.executar(ordemServicoId));
    }

    @PatchMapping("/{ordemServicoId}/falhar")
    @Operation(summary = "Registrar falha na execução",
            description = "EM_EXECUCAO → FALHOU. Publica ExecucaoFalhou: o inventory estorna os insumos, o billing reembolsa e a OS é cancelada.")
    public ExecucaoResponse falhar(@PathVariable String ordemServicoId, @Valid @RequestBody FalharExecucaoRequest request) {
        return ExecucaoPresenter.apresentar(falhar.executar(ordemServicoId, request.motivo()));
    }
}
