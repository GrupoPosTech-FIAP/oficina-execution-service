package com.mecanica.oficina_execution_service.adapters.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Nomes de atributos e índices das tabelas. O mesmo esquema está no Terraform
 * ({@code oficina-infra-database/dynamodb.tf}) e em {@link CriadorTabelasDynamo}.
 *
 * <ul>
 *   <li><b>Execuções:</b> chave {@code ordemServicoId}. O índice {@code status-ordenacao} lista a fila
 *       de um status em ordem de chegada.</li>
 *   <li><b>Outbox:</b> chave {@code eventId}. O índice esparso {@code pendentes} só contém os itens com o
 *       atributo {@code pendente}, que é removido quando o evento é publicado.</li>
 * </ul>
 */
public final class EsquemaDynamo {

    public static final String ORDEM_SERVICO_ID = "ordemServicoId";
    public static final String STATUS = "status";
    public static final String ORDENACAO = "ordenacao";
    public static final String VERSAO = "versao";
    public static final String INDICE_STATUS = "status-ordenacao";

    public static final String EVENT_ID = "eventId";
    public static final String PENDENTE = "pendente";
    public static final String CRIADO_EM = "criadoEm";
    public static final String VALOR_PENDENTE = "PENDENTE";
    public static final String INDICE_PENDENTES = "pendentes";

    /**
     * Datas usadas em chave de ordenação precisam ter largura fixa para que a ordem do texto seja a
     * ordem do tempo ({@code Instant.toString} omite casas decimais zeradas).
     */
    private static final DateTimeFormatter DATA_ORDENAVEL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);

    public static String formatar(Instant instante) {
        return DATA_ORDENAVEL.format(instante);
    }

    private EsquemaDynamo() {
    }
}
