package com.mecanica.oficina_execution_service.adapters.persistence;

import static com.mecanica.oficina_execution_service.adapters.persistence.EsquemaDynamo.*;

import com.mecanica.oficina_execution_service.adapters.messaging.EventoEnvelope;
import com.mecanica.oficina_execution_service.adapters.messaging.FabricaEnvelopes;
import com.mecanica.oficina_execution_service.application.evento.EventoSaga;
import com.mecanica.oficina_execution_service.application.execucao.gateway.ConflitoDeConcorrenciaException;
import com.mecanica.oficina_execution_service.application.execucao.gateway.ExecucaoGateway;
import com.mecanica.oficina_execution_service.domain.execucao.Execucao;
import com.mecanica.oficina_execution_service.domain.execucao.StatusExecucao;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * Execuções e outbox no DynamoDB. A gravação usa {@code TransactWriteItems}: a execução e os eventos
 * que ela gerou entram juntos, o que faz o papel da transação local do padrão Transactional Outbox.
 */
@Component
public class DynamoExecucaoGateway implements ExecucaoGateway {

    private final DynamoDbClient dynamo;
    private final DynamoProperties properties;
    private final FabricaEnvelopes envelopes;

    public DynamoExecucaoGateway(DynamoDbClient dynamo, DynamoProperties properties, FabricaEnvelopes envelopes) {
        this.dynamo = dynamo;
        this.properties = properties;
        this.envelopes = envelopes;
    }

    @Override
    public Optional<Execucao> buscar(String ordemServicoId) {
        var resposta = dynamo.getItem(GetItemRequest.builder()
                .tableName(properties.tabelaExecucoes())
                .key(Map.of(ORDEM_SERVICO_ID, AttributeValue.fromS(ordemServicoId)))
                .consistentRead(true)
                .build());
        return resposta.hasItem() && !resposta.item().isEmpty()
                ? Optional.of(ExecucaoItemMapper.paraExecucao(resposta.item()))
                : Optional.empty();
    }

    @Override
    public List<Execucao> listarPorStatus(StatusExecucao status) {
        List<Execucao> execucoes = new ArrayList<>();
        Map<String, AttributeValue> inicio = null;
        do {
            QueryResponse pagina = dynamo.query(QueryRequest.builder()
                    .tableName(properties.tabelaExecucoes())
                    .indexName(INDICE_STATUS)
                    .keyConditionExpression("#status = :status")
                    .expressionAttributeNames(Map.of("#status", STATUS))
                    .expressionAttributeValues(Map.of(":status", AttributeValue.fromS(status.name())))
                    .exclusiveStartKey(inicio)
                    .build());
            pagina.items().forEach(item -> execucoes.add(ExecucaoItemMapper.paraExecucao(item)));
            inicio = pagina.hasLastEvaluatedKey() && !pagina.lastEvaluatedKey().isEmpty() ? pagina.lastEvaluatedKey() : null;
        } while (inicio != null);
        return execucoes;
    }

    @Override
    public void salvar(Execucao execucao, List<EventoSaga> eventos) {
        List<TransactWriteItem> escritas = new ArrayList<>();
        escritas.add(TransactWriteItem.builder().put(putExecucao(execucao)).build());
        for (EventoSaga evento : eventos) {
            escritas.add(TransactWriteItem.builder().put(putOutbox(envelopes.criar(evento))).build());
        }

        try {
            dynamo.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(escritas).build());
        } catch (TransactionCanceledException e) {
            throw new ConflitoDeConcorrenciaException(execucao.getOrdemServicoId(), e);
        }
    }

    private Put putExecucao(Execucao execucao) {
        var put = Put.builder()
                .tableName(properties.tabelaExecucoes())
                .item(ExecucaoItemMapper.paraItem(execucao));
        if (execucao.getVersao() == 0) {
            return put.conditionExpression("attribute_not_exists(" + ORDEM_SERVICO_ID + ")").build();
        }
        return put.conditionExpression("#versao = :versaoLida")
                .expressionAttributeNames(Map.of("#versao", VERSAO))
                .expressionAttributeValues(Map.of(":versaoLida", AttributeValue.fromN(Long.toString(execucao.getVersao()))))
                .build();
    }

    private Put putOutbox(EventoEnvelope envelope) {
        return Put.builder()
                .tableName(properties.tabelaOutbox())
                .item(Map.of(
                        EVENT_ID, AttributeValue.fromS(envelope.eventId()),
                        "eventType", AttributeValue.fromS(envelope.eventType()),
                        "sagaId", AttributeValue.fromS(envelope.sagaId()),
                        "envelope", AttributeValue.fromS(envelopes.serializar(envelope)),
                        CRIADO_EM, AttributeValue.fromS(formatar(envelope.occurredAt())),
                        "tentativas", AttributeValue.fromN("0"),
                        PENDENTE, AttributeValue.fromS(VALOR_PENDENTE)))
                .conditionExpression("attribute_not_exists(" + EVENT_ID + ")")
                .build();
    }
}
