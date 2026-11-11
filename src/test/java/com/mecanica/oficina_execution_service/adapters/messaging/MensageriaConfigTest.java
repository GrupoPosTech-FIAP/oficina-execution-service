package com.mecanica.oficina_execution_service.adapters.messaging;

import com.mecanica.oficina_execution_service.adapters.persistence.DynamoOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MensageriaConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MensageriaConfig.class)
            .withBean(DynamoOutboxRepository.class, () -> mock(DynamoOutboxRepository.class))
            .withBean(ProcessadorEventos.class, () -> mock(ProcessadorEventos.class))
            .withBean(JsonMapper.class, () -> MensageriaFixture.JSON);

    @Test
    void desligadaPorPadraoNaoCriaClientesNemAgendadores() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MensageriaProperties.class);
            assertThat(ctx).doesNotHaveBean(SnsClient.class);
            assertThat(ctx).doesNotHaveBean(SqsClient.class);
            assertThat(ctx).doesNotHaveBean(OutboxRelay.class);
            assertThat(ctx).doesNotHaveBean(SqsConsumidorEventos.class);
            assertThat(ctx.getBean(MensageriaProperties.class).produtor()).isEqualTo("oficina-execution-service");
        });
    }

    @Test
    void habilitadaCriaClientesApontandoParaOEndpointConfigurado() {
        runner.withPropertyValues(
                        "mensageria.habilitada=true",
                        "mensageria.endpoint=http://localhost:4566",
                        "mensageria.topico-arn=arn:aws:sns:us-east-1:000000000000:oficina-execution-events",
                        "mensageria.fila-url=http://localhost:4566/000000000000/oficina-execution-queue")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SnsClient.class);
                    assertThat(ctx).hasSingleBean(SqsClient.class);
                    assertThat(ctx).hasSingleBean(OutboxRelay.class);
                    assertThat(ctx).hasSingleBean(SqsConsumidorEventos.class);
                    assertThat(ctx.getBean(MensageriaProperties.class).topicoArn()).endsWith("oficina-execution-events");
                });
    }

    @Test
    void habilitadaSemEndpointUsaAAws() {
        runner.withPropertyValues("mensageria.habilitada=true", "mensageria.regiao=sa-east-1")
                .run(ctx -> assertThat(ctx).hasSingleBean(SnsClient.class));
    }

    @Test
    void valoresPadraoDasPropriedades() {
        MensageriaProperties p = new MensageriaProperties(false, " ", null, null, "", null, null, null, null);

        assertThat(p.produtor()).isEqualTo("oficina-execution-service");
        assertThat(p.regiao()).isEqualTo("us-east-1");
        assertThat(p.loteOutbox()).isEqualTo(50);
        assertThat(p.maxMensagens()).isEqualTo(10);
        assertThat(p.esperaSegundos()).isEqualTo(10);
    }
}
