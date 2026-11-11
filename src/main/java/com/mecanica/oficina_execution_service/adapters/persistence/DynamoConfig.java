package com.mecanica.oficina_execution_service.adapters.persistence;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Cliente do DynamoDB. Credenciais vêm da cadeia padrão do SDK: no EKS, a role do nó (LabRole);
 * localmente, variáveis de ambiente.
 */
@Configuration
@EnableConfigurationProperties(DynamoProperties.class)
public class DynamoConfig {

    @Bean(destroyMethod = "close")
    DynamoDbClient dynamoDbClient(DynamoProperties properties) {
        var builder = DynamoDbClient.builder().region(Region.of(properties.regiao()));
        if (properties.temEndpoint()) builder.endpointOverride(URI.create(properties.endpoint()));
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "dynamodb", name = "criar-tabelas", havingValue = "true")
    CriadorTabelasDynamo criadorTabelasDynamo(DynamoDbClient dynamo, DynamoProperties properties) {
        return new CriadorTabelasDynamo(dynamo, properties);
    }
}
