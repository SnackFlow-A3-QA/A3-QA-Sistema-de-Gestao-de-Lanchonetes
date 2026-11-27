package br.com.snackflow.pedido;

import java.io.Serializable;
import java.math.BigDecimal;

/** Opcao selecionada em um item do carrinho guardado na sessao HTTP. */
public record OpcaoCarrinho(
        Integer idOpcao,
        String tipo,
        String nome,
        BigDecimal precoAdicional) implements Serializable {

    private static final long serialVersionUID = 1L;
}
