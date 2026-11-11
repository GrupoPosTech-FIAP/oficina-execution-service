package com.mecanica.oficina_execution_service.adapters.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da mensageria SNS/SQS.
 *
 * @param habilitada        liga o relay da outbox e o consumidor da fila; desligada, os eventos
 *                          continuam sendo gravados na outbox, mas não saem do serviço
 * @param produtor          nome do serviço gravado no campo {@code producer} do envelope
 * @param topicoArn         tópico SNS onde este serviço publica
 * @param filaUrl           fila SQS que este serviço consome
 * @param regiao            região AWS
 * @param endpoint          endpoint alternativo (LocalStack); vazio usa o padrão da AWS
 * @param loteOutbox        quantos eventos pendentes o relay publica por ciclo
 * @param maxMensagens      quantas mensagens o consumidor recebe por chamada (máx. 10)
 * @param esperaSegundos    long polling do consumidor (máx. 20)
 */
@ConfigurationProperties(prefix = "mensageria")
public record MensageriaProperties(
        boolean habilitada,
        String produtor,
        String topicoArn,
        String filaUrl,
        String regiao,
        String endpoint,
        Integer loteOutbox,
        Integer maxMensagens,
        Integer esperaSegundos) {

    public MensageriaProperties {
        if (produtor == null || produtor.isBlank()) produtor = "oficina-execution-service";
        if (regiao == null || regiao.isBlank()) regiao = "us-east-1";
        if (loteOutbox == null) loteOutbox = 50;
        if (maxMensagens == null) maxMensagens = 10;
        if (esperaSegundos == null) esperaSegundos = 10;
    }
}
