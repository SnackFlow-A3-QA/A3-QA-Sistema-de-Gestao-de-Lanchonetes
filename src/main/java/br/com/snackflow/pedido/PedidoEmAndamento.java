package br.com.snackflow.pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Leitura resumida dos pedidos em preparo ou prontos para acompanhamento no PDV. */
public record PedidoEmAndamento(
        Integer idPedido,
        LocalDateTime dataHora,
        String nomeCliente,
        StatusPedido status,
        FormaPagamento formaPagamento,
        BigDecimal valorTotal,
        String resumoItens) {
}
