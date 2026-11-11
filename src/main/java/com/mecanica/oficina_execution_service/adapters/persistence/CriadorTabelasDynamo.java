package com.mecanica.oficina_execution_service.adapters.persistence;

import static com.mecanica.oficina_execution_service.adapters.persistence.EsquemaDynamo.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Cria as tabelas na subida quando {@code dynamodb.criar-tabelas=true} (docker-compose e testes).
 * Na AWS as tabelas vêm do Terraform, com o mesmo esquema.
 */
public class CriadorTabelasDynamo implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(CriadorTabelasDynamo.class);

    private final DynamoDbClient dynamo;
    private final DynamoProperties properties;

    public CriadorTabelasDynamo(DynamoDbClient dynamo, DynamoProperties properties) {
        this.dynamo = dynamo;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        criar(CreateTableRequest.builder()
                .tableName(properties.tabelaExecucoes())
                .attributeDefinitions(texto(ORDEM_SERVICO_ID), texto(STATUS), texto(ORDENACAO))
                .keySchema(chave(ORDEM_SERVICO_ID, KeyType.HASH))
                .globalSecondaryIndexes(indice(INDICE_STATUS, STATUS, ORDENACAO))
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
        criar(CreateTableRequest.builder()
                .tableName(properties.tabelaOutbox())
                .attributeDefinitions(texto(EVENT_ID), texto(PENDENTE), texto(CRIADO_EM))
                .keySchema(chave(EVENT_ID, KeyType.HASH))
                .globalSecondaryIndexes(indice(INDICE_PENDENTES, PENDENTE, CRIADO_EM))
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    private void criar(CreateTableRequest request) {
        try {
            dynamo.createTable(request);
            dynamo.waiter().waitUntilTableExists(b -> b.tableName(request.tableName()));
            log.info("[DYNAMODB_TABELA_CRIADA] tabela={}", request.tableName());
        } catch (ResourceInUseException jaExiste) {
            log.info("[DYNAMODB_TABELA_EXISTENTE] tabela={}", request.tableName());
        }
    }

    private static AttributeDefinition texto(String nome) {
        return AttributeDefinition.builder().attributeName(nome).attributeType(ScalarAttributeType.S).build();
    }

    private static KeySchemaElement chave(String nome, KeyType tipo) {
        return KeySchemaElement.builder().attributeName(nome).keyType(tipo).build();
    }

    private static GlobalSecondaryIndex indice(String nome, String particao, String ordenacao) {
        return GlobalSecondaryIndex.builder()
                .indexName(nome)
                .keySchema(chave(particao, KeyType.HASH), chave(ordenacao, KeyType.RANGE))
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }
}
