package br.com.snackflow.pedido;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Carrinho do PDV armazenado na sessao HTTP antes da criacao do pedido ABERTO no banco. */
public class CarrinhoPedido implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<ItemCarrinho> itens = new ArrayList<>();
    private Integer idCliente;
    private String nomeCliente;
    private FormaPagamento formaPagamento;
    private String observacao;

    public List<ItemCarrinho> getItens() {
        return List.copyOf(itens);
    }

    public Integer getIdCliente() {
        return idCliente;
    }

    public String getNomeCliente() {
        return nomeCliente;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public String getObservacao() {
        return observacao;
    }

    public boolean estaVazio() {
        return itens.isEmpty();
    }

    public int getQuantidadeTotalItens() {
        return itens.stream().mapToInt(ItemCarrinho::quantidade).sum();
    }

    public int quantidadeDoProduto(Integer idProduto) {
        if (idProduto == null) {
            return 0;
        }
        return itens.stream()
                .filter(item -> idProduto.equals(item.idProduto()))
                .mapToInt(ItemCarrinho::quantidade)
                .sum();
    }

    public BigDecimal getValorTotal() {
        return itens.stream()
                .map(ItemCarrinho::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    void adicionarItem(ItemCarrinho item) {
        itens.add(item);
    }

    void substituirItem(int indice, ItemCarrinho item) {
        itens.set(indice, item);
    }

    void removerItemPorIndice(int indice) {
        itens.remove(indice);
    }

    int indicePorUid(String uid) {
        for (int i = 0; i < itens.size(); i++) {
            if (itens.get(i).uid().equals(uid)) {
                return i;
            }
        }
        return -1;
    }

    void definirCliente(Integer idCliente, String nomeCliente) {
        this.idCliente = idCliente;
        this.nomeCliente = nomeCliente;
    }

    void definirFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = formaPagamento;
    }

    void definirObservacao(String observacao) {
        this.observacao = observacao;
    }

    void limpar() {
        itens.clear();
        idCliente = null;
        nomeCliente = null;
        formaPagamento = null;
        observacao = null;
    }
}
