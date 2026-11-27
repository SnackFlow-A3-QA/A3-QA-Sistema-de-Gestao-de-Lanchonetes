package br.com.snackflow.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.snackflow.common.RegraNegocioException;
import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock
    private PedidoCarrinhoService carrinhoService;

    @Mock
    private PedidoService pedidoService;

    @InjectMocks
    private PedidoController controller;

    private MockHttpSession sessao;
    private RedirectAttributesModelMap flash;
    private UsuarioLogado caixa;

    @BeforeEach
    void configurar() {
        sessao = new MockHttpSession();
        flash = new RedirectAttributesModelMap();
        caixa = new UsuarioLogado(2, "Ana", "ana.caixa", "hash", Perfil.CAIXA, true);
    }

    @Test
    void exibirPdvDeveFiltrarProdutosECarregarModeloECarrinhoDaSessao() {
        ProdutoDisponivel p1 = new ProdutoDisponivel(1, "X-Burger", "Lanches", new BigDecimal("25.00"), true, 4);
        ProdutoDisponivel p2 = new ProdutoDisponivel(2, "Suco Laranja", "Bebidas", new BigDecimal("9.00"), true, 10);
        when(carrinhoService.listarProdutosDoCardapio()).thenReturn(List.of(p1, p2));
        when(carrinhoService.listarOpcoesAgrupadasPorProduto()).thenReturn(Map.of());
        when(pedidoService.listarPedidosEmAndamento()).thenReturn(List.of());

        Model modelo = new ExtendedModelMap();
        String view = controller.exibirPdv("suco", "Bebidas", sessao, modelo);

        assertThat(view).isEqualTo("pedido/pdv");
        assertThat(modelo.getAttribute("produtos")).isEqualTo(List.of(p2));
        assertThat(modelo.getAttribute("categorias")).isEqualTo(List.of("Lanches", "Bebidas"));
        assertThat(sessao.getAttribute(PedidoController.CHAVE_CARRINHO_SESSAO)).isInstanceOf(CarrinhoPedido.class);
    }

    @Test
    void deveDelegarOperacoesDoCarrinhoParaPedidoCarrinhoService() {
        CarrinhoPedido carrinho = new CarrinhoPedido();
        sessao.setAttribute(PedidoController.CHAVE_CARRINHO_SESSAO, carrinho);

        AdicionarItemForm form = new AdicionarItemForm();
        form.setIdProduto(1);
        form.setQuantidade(2);
        form.setIdsOpcoes(List.of(10));
        form.setObservacao("Sem cebola");

        assertThat(controller.adicionarItem(form, sessao, flash)).isEqualTo("redirect:/pdv");
        verify(carrinhoService).adicionarItem(carrinho, 1, 2, List.of(10), "Sem cebola");
        assertThat(flash.getFlashAttributes().get("ok")).isEqualTo("Item adicionado ao pedido.");

        assertThat(controller.alterarQuantidade("u1", 3, sessao)).isEqualTo("redirect:/pdv");
        verify(carrinhoService).alterarQuantidade(carrinho, "u1", 3);

        AdicionarItemForm formOpcoes = new AdicionarItemForm();
        formOpcoes.setIdsOpcoes(List.of(11));
        formOpcoes.setObservacao("Cortar");
        assertThat(controller.atualizarOpcoesItem("u1", formOpcoes, sessao, flash)).isEqualTo("redirect:/pdv");
        verify(carrinhoService).atualizarOpcoesEObservacao(carrinho, "u1", List.of(11), "Cortar");

        assertThat(controller.removerItem("u1", sessao, flash)).isEqualTo("redirect:/pdv");
        verify(carrinhoService).removerItem(carrinho, "u1");

        assertThat(controller.vincularCliente(5, sessao, flash)).isEqualTo("redirect:/pdv");
        verify(carrinhoService).definirCliente(carrinho, 5);

        assertThat(controller.limparCarrinho(sessao, flash)).isEqualTo("redirect:/pdv");
        verify(carrinhoService).limparCarrinho(carrinho);
    }

    @Test
    void confirmarEntregarECancelarDevemPassarUsuarioLogadoParaPedidoService() {
        CarrinhoPedido carrinho = new CarrinhoPedido();
        sessao.setAttribute(PedidoController.CHAVE_CARRINHO_SESSAO, carrinho);
        FinalizarPedidoForm form = new FinalizarPedidoForm();
        form.setFormaPagamento(FormaPagamento.PIX);
        form.setObservacao("Viagem");

        when(pedidoService.confirmarPedido(carrinho, 2)).thenReturn(55);

        String redirect = controller.confirmarPedido(form, caixa, sessao, flash);
        assertThat(redirect).isEqualTo("redirect:/pdv");
        verify(carrinhoService).definirDadosFinalizacao(carrinho, FormaPagamento.PIX, "Viagem");
        assertThat(flash.getFlashAttributes().get("ok"))
                .isEqualTo("Pedido #55 confirmado e enviado para a cozinha.");

        controller.entregarPedido(55, caixa, flash);
        verify(pedidoService).entregarPedido(55, 2);

        controller.cancelarPedido(56, caixa, flash);
        verify(pedidoService).cancelarPedido(56, 2);
    }

    @Test
    void deveTraduzirRegraNegocioExceptionParaFlashErro() {
        doThrow(new RegraNegocioException("Estoque insuficiente"))
                .when(pedidoService).cancelarPedido(99, 2);

        try {
            controller.cancelarPedido(99, caixa, flash);
        } catch (RegraNegocioException ex) {
            String view = controller.tratarRegraNegocio(ex, flash);
            assertThat(view).isEqualTo("redirect:/pdv");
            assertThat(flash.getFlashAttributes().get("erro")).isEqualTo("Estoque insuficiente");
        }
    }
}
