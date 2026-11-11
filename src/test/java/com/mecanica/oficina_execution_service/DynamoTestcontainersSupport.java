package com.mecanica.oficina_execution_service;

import static com.mecanica.oficina_execution_service.adapters.persistence.EsquemaDynamo.EVENT_ID;
import static com.mecanica.oficina_execution_service.adapters.persistence.EsquemaDynamo.ORDEM_SERVICO_ID;

import com.mecanica.oficina_execution_service.adapters.persistence.DynamoProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * DynamoDB Local num container, compartilhado por todos os testes de integração. As tabelas são
 * criadas pelo {@code CriadorTabelasDynamo} ({@code dynamodb.criar-tabelas=true}) e esvaziadas antes
 * de cada teste.
 */
public abstract class DynamoTestcontainersSupport {

    static final GenericContainer<?> DYNAMO =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.6.1"))
                    .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
                    .withExposedPorts(8000);

    static {
        DYNAMO.start();
        // o DynamoDB Local aceita qualquer credencial, mas o SDK exige alguma
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @DynamicPropertySource
    static void dynamo(DynamicPropertyRegistry registry) {
        registry.add("dynamodb.endpoint", () -> "http://" + DYNAMO.getHost() + ":" + DYNAMO.getMappedPort(8000));
    }

    @Autowired
    protected DynamoDbClient dynamo;

    @Autowired
    protected DynamoProperties dynamoProperties;

    @BeforeEach
    void limparTabelas() {
        limpar(dynamoProperties.tabelaExecucoes(), ORDEM_SERVICO_ID);
        limpar(dynamoProperties.tabelaOutbox(), EVENT_ID);
    }

    private void limpar(String tabela, String chave) {
        dynamo.scanPaginator(b -> b.tableName(tabela)).items()
                .forEach(item -> dynamo.deleteItem(b -> b.tableName(tabela).key(Map.of(chave, item.get(chave)))));
    }

    protected List<Map<String, AttributeValue>> itensDaOutbox() {
        return dynamo.scan(b -> b.tableName(dynamoProperties.tabelaOutbox())).items();
    }
}
