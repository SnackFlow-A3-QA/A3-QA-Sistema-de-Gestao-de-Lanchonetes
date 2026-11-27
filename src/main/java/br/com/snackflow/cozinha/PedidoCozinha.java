package br.com.snackflow.cozinha;

import java.time.LocalDateTime;
import java.util.List;

/** Pedido em preparo agrupado a partir de vw_cozinha, ordenado do mais antigo ao mais novo. */
public record PedidoCozinha(
        Integer idPedido,
        LocalDateTime dataHora,
        String cliente,
        String observacao,
        List<ItemPedidoCozinha> itens,
        boolean prioritario) {

    public PedidoCozinha {
        itens = itens == null ? List.of() : List.copyOf(itens);
    }
}
