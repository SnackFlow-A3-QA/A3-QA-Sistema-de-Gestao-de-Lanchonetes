package br.com.snackflow.pedido;

import java.math.BigDecimal;

/** Leitura das opcoes ativas do produto na tabela produto_opcoes. */
public record OpcaoProduto(
        Integer idOpcao,
        Integer idProduto,
        String tipo,
        String nome,
        BigDecimal precoAdicional,
        boolean ativo) {

    public boolean ehAdicional() {
        return "ADICIONAL".equals(tipo);
    }
}
