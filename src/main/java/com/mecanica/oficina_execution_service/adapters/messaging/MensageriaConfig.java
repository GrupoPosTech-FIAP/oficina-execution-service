package com.mecanica.oficina_execution_service.adapters.messaging;

import com.mecanica.oficina_execution_service.adapters.persistence.DynamoOutboxRepository;
import java.net.URI;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(MensageriaProperties.class)
public class MensageriaConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Relay e consumidor só sobem com {@code mensageria.habilitada=true}. Credenciais vêm da
     * cadeia padrão do SDK: no EKS, a role do nó (LabRole); localmente, variáveis de ambiente.
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "mensageria", name = "habilitada", havingValue = "true")
    static class Habilitada {

        @Bean(destroyMethod = "close")
        SnsClient snsClient(MensageriaProperties properties) {
            var builder = SnsClient.builder().region(Region.of(properties.regiao()));
            if (temEndpoint(properties)) builder.endpointOverride(URI.create(properties.endpoint()));
            return builder.build();
        }

        @Bean(destroyMethod = "close")
        SqsClient sqsClient(MensageriaProperties properties) {
            var builder = SqsClient.builder().region(Region.of(properties.regiao()));
            if (temEndpoint(properties)) builder.endpointOverride(URI.create(properties.endpoint()));
            return builder.build();
        }

        @Bean
        OutboxRelay outboxRelay(DynamoOutboxRepository repository, SnsClient snsClient,
                                MensageriaProperties properties, Clock clock) {
            return new OutboxRelay(repository, snsClient, properties, clock);
        }

        @Bean
        SqsConsumidorEventos sqsConsumidorEventos(SqsClient sqsClient, ProcessadorEventos processador,
                                                  JsonMapper jsonMapper, MensageriaProperties properties) {
            return new SqsConsumidorEventos(sqsClient, processador, jsonMapper, properties);
        }

        private static boolean temEndpoint(MensageriaProperties properties) {
            return properties.endpoint() != null && !properties.endpoint().isBlank();
        }
    }
}
