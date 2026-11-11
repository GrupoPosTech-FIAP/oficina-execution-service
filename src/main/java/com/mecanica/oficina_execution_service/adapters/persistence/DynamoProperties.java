package com.mecanica.oficina_execution_service.adapters.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do DynamoDB.
 *
 * @param tabelaExecucoes tabela das execuções (chave {@code ordemServicoId})
 * @param tabelaOutbox    tabela da outbox de eventos (chave {@code eventId})
 * @param regiao          região AWS
 * @param endpoint        endpoint alternativo (DynamoDB Local / LocalStack); vazio usa a AWS
 * @param criarTabelas    cria as tabelas na subida se não existirem. Só para ambiente local e testes:
 *                        na AWS as tabelas vêm do Terraform ({@code oficina-infra-database/dynamodb.tf})
 */
@ConfigurationProperties(prefix = "dynamodb")
public record DynamoProperties(
        String tabelaExecucoes,
        String tabelaOutbox,
        String regiao,
        String endpoint,
        boolean criarTabelas) {

    public DynamoProperties {
        if (tabelaExecucoes == null || tabelaExecucoes.isBlank()) tabelaExecucoes = "oficina-execucoes";
        if (tabelaOutbox == null || tabelaOutbox.isBlank()) tabelaOutbox = "oficina-execution-outbox";
        if (regiao == null || regiao.isBlank()) regiao = "us-east-1";
    }

    public boolean temEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
