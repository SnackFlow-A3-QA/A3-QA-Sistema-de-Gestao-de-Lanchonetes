package br.com.snackflow.pedido;

/** Estados do ciclo de vida do pedido na tabela pedidos. */
public enum StatusPedido {
    ABERTO,
    EM_PREPARO,
    PRONTO,
    ENTREGUE,
    CANCELADO
}
