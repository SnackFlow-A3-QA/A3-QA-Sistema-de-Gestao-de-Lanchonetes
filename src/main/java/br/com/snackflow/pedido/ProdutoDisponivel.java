package br.com.snackflow.pedido;

import java.math.BigDecimal;

/** Leitura da view vw_disponibilidade_produtos para o cardapio do PDV. */
public record ProdutoDisponivel(
        Integer idProduto,
        String nome,
        String categoria,
        BigDecimal preco,
        boolean ativo,
        int disponivel) {

    public boolean estaDisponivel() {
        return ativo && disponivel > 0;
    }
}
