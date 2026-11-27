package br.com.snackflow.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CarrinhoPedidoTest {

    @Test
    void deveCalcularSubtotaisETotalDoCarrinhoComAdicionaisERemocoes() {
        CarrinhoPedido carrinho = new CarrinhoPedido();
        assertThat(carrinho.estaVazio()).isTrue();
        assertThat(carrinho.getQuantidadeTotalItens()).isZero();
        assertThat(carrinho.getValorTotal()).isEqualByComparingTo("0.00");

        ItemCarrinho hamburguer = new ItemCarrinho(
                "item-1",
                10,
                "X-Burger",
                new BigDecimal("25.00"),
                2,
                List.of(
                        new OpcaoCarrinho(1, "ADICIONAL", "Bacon", new BigDecimal("5.50")),
                        new OpcaoCarrinho(2, "REMOVER", "Cebola", BigDecimal.ZERO)),
                "Bem passado");
        ItemCarrinho suco = new ItemCarrinho(
                "item-2",
                11,
                "Suco",
                new BigDecimal("10.00"),
                1,
                null,
                null);

        carrinho.adicionarItem(hamburguer);
        carrinho.adicionarItem(suco);

        assertThat(hamburguer.precoAdicionais()).isEqualByComparingTo("5.50");
        assertThat(hamburguer.precoUnitarioComAdicionais()).isEqualByComparingTo("30.50");
        assertThat(hamburguer.subtotal()).isEqualByComparingTo("61.00");
        assertThat(suco.opcoes()).isEmpty();
        assertThat(suco.subtotal()).isEqualByComparingTo("10.00");
        assertThat(carrinho.estaVazio()).isFalse();
        assertThat(carrinho.getQuantidadeTotalItens()).isEqualTo(3);
        assertThat(carrinho.quantidadeDoProduto(10)).isEqualTo(2);
        assertThat(carrinho.quantidadeDoProduto(null)).isZero();
        assertThat(carrinho.getValorTotal()).isEqualByComparingTo("71.00");
    }

    @Test
    void deveSerializarEDesserializarCarrinhoDaSessaoSemErros() throws Exception {
        CarrinhoPedido original = new CarrinhoPedido();
        original.definirCliente(7, "Maria Silva");
        original.definirFormaPagamento(FormaPagamento.PIX);
        original.definirObservacao("Sem pimenta");
        original.adicionarItem(new ItemCarrinho(
                "uid-1",
                1,
                "X-Salada",
                new BigDecimal("22.00"),
                2,
                List.of(new OpcaoCarrinho(3, "ADICIONAL", "Cheddar", new BigDecimal("4.00"))),
                "Ao ponto"));

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(saida)) {
            oos.writeObject(original);
        }

        CarrinhoPedido restaurado;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(saida.toByteArray()))) {
            restaurado = (CarrinhoPedido) ois.readObject();
        }

        assertThat(restaurado.getIdCliente()).isEqualTo(7);
        assertThat(restaurado.getNomeCliente()).isEqualTo("Maria Silva");
        assertThat(restaurado.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(restaurado.getObservacao()).isEqualTo("Sem pimenta");
        assertThat(restaurado.getItens()).hasSize(1);
        assertThat(restaurado.getValorTotal()).isEqualByComparingTo("52.00");
    }

    @Test
    void deveRetornarListaDeItensImutavel() {
        CarrinhoPedido carrinho = new CarrinhoPedido();
        ItemCarrinho item = new ItemCarrinho(
                "uid-1", 1, "Agua", new BigDecimal("5.00"), 1, List.of(), null);
        List<ItemCarrinho> visao = carrinho.getItens();
        assertThatThrownBy(() -> visao.add(item))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
