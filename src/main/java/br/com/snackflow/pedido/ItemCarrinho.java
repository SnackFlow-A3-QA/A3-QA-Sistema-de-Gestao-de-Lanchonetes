package br.com.snackflow.pedido;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Item do carrinho guardado na sessao HTTP. Valor calculado como (preco + adicionais) * quantidade. */
public record ItemCarrinho(
        String uid,
        Integer idProduto,
        String nomeProduto,
        BigDecimal precoUnitario,
        int quantidade,
        List<OpcaoCarrinho> opcoes,
        String observacao) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ItemCarrinho {
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
    }

    public BigDecimal precoAdicionais() {
        return opcoes.stream()
                .map(OpcaoCarrinho::precoAdicional)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal precoUnitarioComAdicionais() {
        return precoUnitario.add(precoAdicionais());
    }

    public BigDecimal subtotal() {
        return precoUnitarioComAdicionais().multiply(BigDecimal.valueOf(quantidade));
    }

    ItemCarrinho comQuantidade(int novaQuantidade) {
        return new ItemCarrinho(uid, idProduto, nomeProduto, precoUnitario, novaQuantidade, opcoes, observacao);
    }

    ItemCarrinho comOpcoesEObservacao(List<OpcaoCarrinho> novasOpcoes, String novaObservacao) {
        return new ItemCarrinho(uid, idProduto, nomeProduto, precoUnitario, quantidade, novasOpcoes, novaObservacao);
    }
}
