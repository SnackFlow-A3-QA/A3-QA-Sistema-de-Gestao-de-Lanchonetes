package br.com.snackflow.pedido;

import java.util.ArrayList;
import java.util.List;

/** Formulario para adicionar produto ao carrinho do PDV com opcoes e observacao. */
public class AdicionarItemForm {

    private Integer idProduto;
    private Integer quantidade = 1;
    private List<Integer> idsOpcoes = new ArrayList<>();
    private String observacao;

    public Integer getIdProduto() {
        return idProduto;
    }

    public void setIdProduto(Integer idProduto) {
        this.idProduto = idProduto;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public List<Integer> getIdsOpcoes() {
        return idsOpcoes;
    }

    public void setIdsOpcoes(List<Integer> idsOpcoes) {
        this.idsOpcoes = idsOpcoes == null ? new ArrayList<>() : idsOpcoes;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
