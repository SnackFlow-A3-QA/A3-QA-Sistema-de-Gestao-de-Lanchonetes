package br.com.snackflow.pedido;

/** Formas de pagamento aceitas pela coluna pedidos.forma_pagamento. */
public enum FormaPagamento {
    DINHEIRO("Dinheiro"),
    CREDITO("Crédito"),
    DEBITO("Débito"),
    PIX("PIX"),
    OUTRO("Outro");

    private final String rotulo;

    FormaPagamento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
