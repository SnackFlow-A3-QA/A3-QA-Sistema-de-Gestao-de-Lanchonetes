package br.com.snackflow.cozinha;

import java.util.List;

/** Item exibido no cartao do pedido na tela da cozinha. */
public record ItemPedidoCozinha(
        String produto,
        int quantidade,
        List<OpcaoItemCozinha> opcoes,
        String observacao) {

    public ItemPedidoCozinha {
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
    }
}
