package com.mecanica.oficina_execution_service.adapters.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mecanica.oficina_execution_service.adapters.persistence.DynamoOutboxRepository;
import com.mecanica.oficina_execution_service.adapters.persistence.DynamoOutboxRepository.EventoPendente;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

class OutboxRelayTest {

    private DynamoOutboxRepository repository;
    private SnsClient sns;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        repository = mock(DynamoOutboxRepository.class);
        sns = mock(SnsClient.class);
        relay = new OutboxRelay(repository, sns, MensageriaFixture.properties(),
                Clock.fixed(MensageriaFixture.AGORA, ZoneOffset.UTC));
    }

    @Test
    void publicaComOAtributoDoFiltroEMarcaComoPublicado() {
        when(repository.pendentes(50)).thenReturn(List.of(
                new EventoPendente("ev-1", "ExecucaoIniciada", "os-1", "{\"eventId\":\"ev-1\"}", 0)));

        relay.publicarPendentes();

        ArgumentCaptor<PublishRequest> publish = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(publish.capture());
        assertThat(publish.getValue().topicArn()).endsWith("oficina-execution-events");
        assertThat(publish.getValue().message()).isEqualTo("{\"eventId\":\"ev-1\"}");
        assertThat(publish.getValue().messageAttributes().get("eventType").stringValue()).isEqualTo("ExecucaoIniciada");
        verify(repository).marcarPublicado("ev-1", MensageriaFixture.AGORA);
    }

    @Test
    void falhaNoSnsDeixaPendenteEContinuaOLote() {
        when(repository.pendentes(50)).thenReturn(List.of(
                new EventoPendente("ev-1", "ExecucaoIniciada", "os-1", "{}", 2),
                new EventoPendente("ev-2", "ExecucaoConcluida", "os-1", "{}", 0)));
        when(sns.publish(any(PublishRequest.class)))
                .thenThrow(SnsException.builder().message("throttled").build())
                .thenReturn(null);

        relay.publicarPendentes();

        verify(repository).registrarFalha(eq("ev-1"), anyString());
        verify(repository, never()).marcarPublicado(eq("ev-1"), any());
        verify(repository).marcarPublicado("ev-2", MensageriaFixture.AGORA);
    }

    @Test
    void mensagemDeErroLongaETruncada() {
        when(repository.pendentes(50)).thenReturn(List.of(new EventoPendente("ev-1", "ExecucaoIniciada", "os-1", "{}", 0)));
        when(sns.publish(any(PublishRequest.class))).thenThrow(new IllegalStateException("x".repeat(5000)));

        relay.publicarPendentes();

        ArgumentCaptor<String> erro = ArgumentCaptor.forClass(String.class);
        verify(repository).registrarFalha(eq("ev-1"), erro.capture());
        assertThat(erro.getValue()).hasSize(1000);
    }
}
