package br.com.snackflow.cozinha;

import java.time.LocalDateTime;

/** Linha retornada pela view vw_cozinha (sem id_item). */
public record LinhaCozinha(
        Integer idPedido,
        LocalDateTime dataHora,
        String cliente,
        String observacaoPedido,
        String produto,
        int quantidade,
        String opcoes,
        String observacaoItem) {
}
