package com.mecanica.oficina_execution_service.adapters.persistence;

import static com.mecanica.oficina_execution_service.adapters.persistence.EsquemaDynamo.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/** Leitura dos eventos pendentes da outbox e registro do resultado da publicação. */
@Component
public class DynamoOutboxRepository {

    public record EventoPendente(String eventId, String eventType, String sagaId, String envelope, int tentativas) {
    }

    private final DynamoDbClient dynamo;
    private final DynamoProperties properties;

    public DynamoOutboxRepository(DynamoDbClient dynamo, DynamoProperties properties) {
        this.dynamo = dynamo;
        this.properties = properties;
    }

    /** Pendentes em ordem de criação (índice esparso {@code pendentes}). */
    public List<EventoPendente> pendentes(int limite) {
        return dynamo.query(QueryRequest.builder()
                        .tableName(properties.tabelaOutbox())
                        .indexName(INDICE_PENDENTES)
                        .keyConditionExpression("#pendente = :pendente")
                        .expressionAttributeNames(Map.of("#pendente", PENDENTE))
                        .expressionAttributeValues(Map.of(":pendente", AttributeValue.fromS(VALOR_PENDENTE)))
                        .scanIndexForward(true)
                        .limit(limite)
                        .build())
                .items().stream()
                .map(item -> new EventoPendente(
                        item.get(EVENT_ID).s(),
                        item.get("eventType").s(),
                        item.get("sagaId").s(),
                        item.get("envelope").s(),
                        Integer.parseInt(item.get("tentativas").n())))
                .toList();
    }

    /** Remove o atributo {@code pendente}, o que tira o evento do índice. */
    public void marcarPublicado(String eventId, Instant publicadoEm) {
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(properties.tabelaOutbox())
                .key(Map.of(EVENT_ID, AttributeValue.fromS(eventId)))
                .updateExpression("SET publicadoEm = :publicadoEm REMOVE #pendente, ultimoErro")
                .expressionAttributeNames(Map.of("#pendente", PENDENTE))
                .expressionAttributeValues(Map.of(":publicadoEm", AttributeValue.fromS(publicadoEm.toString())))
                .build());
    }

    public void registrarFalha(String eventId, String erro) {
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(properties.tabelaOutbox())
                .key(Map.of(EVENT_ID, AttributeValue.fromS(eventId)))
                .updateExpression("SET ultimoErro = :erro ADD tentativas :um")
                .expressionAttributeValues(Map.of(
                        ":erro", AttributeValue.fromS(erro == null ? "sem mensagem" : erro),
                        ":um", AttributeValue.fromN("1")))
                .build());
    }
}
