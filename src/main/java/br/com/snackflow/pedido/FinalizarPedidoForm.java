package br.com.snackflow.pedido;

/** Formulario da tela do PDV para confirmar o pedido com forma de pagamento e observacao. */
public class FinalizarPedidoForm {

    private FormaPagamento formaPagamento;
    private String observacao;

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public void setFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = formaPagamento;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
