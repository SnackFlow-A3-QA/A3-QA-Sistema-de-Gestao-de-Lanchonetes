package br.com.snackflow.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.snackflow.common.RegraNegocioException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PedidoCarrinhoServiceTest {

    @Mock
    private PedidoRepository repositorio;

    @InjectMocks
    private PedidoCarrinhoService service;

    private CarrinhoPedido carrinho;
    private ProdutoDisponivel xBurger;
    private OpcaoProduto adicionalBacon;
    private OpcaoProduto removerCebola;

    @BeforeEach
    void configurar() {
        carrinho = new CarrinhoPedido();
        xBurger = new ProdutoDisponivel(1, "X-Burger", "Lanches", new BigDecimal("25.00"), true, 5);
        adicionalBacon = new OpcaoProduto(10, 1, "ADICIONAL", "Bacon", new BigDecimal("4.50"), true);
        removerCebola = new OpcaoProduto(11, 1, "REMOVER", "Cebola", BigDecimal.ZERO, true);
    }

    @Test
    void listarProdutosDoCardapioDeveRetornarListaDoRepositorioEListaVaziaQuandoSemProdutos() {
        when(repositorio.listarProdutosAtivos()).thenReturn(List.of(xBurger));
        assertThat(service.listarProdutosDoCardapio()).containsExactly(xBurger);

        when(repositorio.listarProdutosAtivos()).thenReturn(List.of());
        assertThat(service.listarProdutosDoCardapio()).isEmpty();
    }

    @Test
    void listarOpcoesDoProdutoDeveRetornarOpcoesQuandoProdutoAtivoEFalharQuandoInvalido() {
        when(repositorio.buscarProdutoDisponivel(1)).thenReturn(Optional.of(xBurger));
        when(repositorio.listarOpcoesAtivasPorProduto(1)).thenReturn(List.of(adicionalBacon, removerCebola));

        assertThat(service.listarOpcoesDoProduto(1)).containsExactly(adicionalBacon, removerCebola);

        when(repositorio.listarTodasOpcoesAtivas()).thenReturn(List.of(adicionalBacon, removerCebola));
        assertThat(service.listarOpcoesAgrupadasPorProduto()).containsKey(1);

        assertThatThrownBy(() -> service.listarOpcoesDoProduto(null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_PRODUTO_NAO_ENCONTRADO);

        when(repositorio.buscarProdutoDisponivel(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listarOpcoesDoProduto(99))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_PRODUTO_NAO_ENCONTRADO);
    }

    @Test
    void adicionarItemDeveAdicionarEMesclarItensIdenticosESomarTotal() {
        when(repositorio.buscarProdutoDisponivel(1)).thenReturn(Optional.of(xBurger));
        when(repositorio.listarOpcoesAtivasPorProduto(1)).thenReturn(List.of(adicionalBacon, removerCebola));

        service.adicionarItem(carrinho, 1, 2, List.of(11, 10), "  Sem maionese  ");
        service.adicionarItem(carrinho, 1, 1, List.of(10, 11), "Sem maionese");

        assertThat(carrinho.getItens()).hasSize(1);
        ItemCarrinho item = carrinho.getItens().getFirst();
        assertThat(item.quantidade()).isEqualTo(3);
        assertThat(item.observacao()).isEqualTo("Sem maionese");
        assertThat(item.subtotal()).isEqualByComparingTo("88.50");
        assertThat(carrinho.getValorTotal()).isEqualByComparingTo("88.50");
    }

    @Test
    void adicionarItemDeveSepararLinhasQuandoOpcoesOuObservacoesForemDiferentes() {
        when(repositorio.buscarProdutoDisponivel(1)).thenReturn(Optional.of(xBurger));
        when(repositorio.listarOpcoesAtivasPorProduto(1)).thenReturn(List.of(adicionalBacon));

        service.adicionarItem(carrinho, 1, 2, List.of(), null);
        service.adicionarItem(carrinho, 1, 2, List.of(10), "   ");

        assertThat(carrinho.getItens()).hasSize(2);
        assertThat(carrinho.quantidadeDoProduto(1)).isEqualTo(4);
        assertThat(carrinho.getValorTotal()).isEqualByComparingTo("109.00");
    }

    @Test
    void adicionarItemDeveFalharParaEntradasInvalidasIndisponivelEstoqueOuOpcaoInvalida() {
        assertThatThrownBy(() -> service.adicionarItem(null, 1, 1, List.of(), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_CARRINHO_INVALIDO);

        assertThatThrownBy(() -> service.adicionarItem(carrinho, 0, 1, List.of(), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_PRODUTO_NAO_ENCONTRADO);

        assertThatThrownBy(() -> service.adicionarItem(carrinho, 1, 0, List.of(), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_QUANTIDADE_INVALIDA);

        ProdutoDisponivel inativo = new ProdutoDisponivel(2, "Antigo", "Lanches", BigDecimal.TEN, false, 10);
        when(repositorio.buscarProdutoDisponivel(2)).thenReturn(Optional.of(inativo));
        assertThatThrownBy(() -> service.adicionarItem(carrinho, 2, 1, List.of(), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_PRODUTO_NAO_ENCONTRADO);

        ProdutoDisponivel zerado = new ProdutoDisponivel(3, "Esgotado", "Lanches", BigDecimal.TEN, true, 0);
        when(repositorio.buscarProdutoDisponivel(3)).thenReturn(Optional.of(zerado));
        assertThatThrownBy(() -> service.adicionarItem(carrinho, 3, 1, List.of(), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_PRODUTO_INDISPONIVEL);

        when(repositorio.buscarProdutoDisponivel(1)).thenReturn(Optional.of(xBurger));
        assertThatThrownBy(() -> service.adicionarItem(carrinho, 1, 6, List.of(), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_ESTOQUE_INSUFICIENTE);

        String obsLonga = "a".repeat(256);
        assertThatThrownBy(() -> service.adicionarItem(carrinho, 1, 1, List.of(), obsLonga))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_OBSERVACAO_LONGA);

        when(repositorio.listarOpcoesAtivasPorProduto(1)).thenReturn(List.of(adicionalBacon));
        assertThatThrownBy(() -> service.adicionarItem(carrinho, 1, 1, List.of(999), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_OPCAO_INVALIDA);

        assertThatThrownBy(() -> service.adicionarItem(carrinho, 1, 1, Arrays.asList(10, 10), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_OPCAO_INVALIDA);
    }

    @Test
    void alterarQuantidadeDeveAtualizarComSucessoEValidarEstoqueELimites() {
        when(repositorio.buscarProdutoDisponivel(1)).thenReturn(Optional.of(xBurger));
        service.adicionarItem(carrinho, 1, 2, List.of(), "Normal");
        service.adicionarItem(carrinho, 1, 1, List.of(), "Extra");
        String primeiroUid = carrinho.getItens().getFirst().uid();

        service.alterarQuantidade(carrinho, primeiroUid, 4);
        assertThat(carrinho.quantidadeDoProduto(1)).isEqualTo(5);

        service.alterarQuantidade(carrinho, primeiroUid, 1);
        assertThat(carrinho.quantidadeDoProduto(1)).isEqualTo(2);

        assertThatThrownBy(() -> service.alterarQuantidade(carrinho, primeiroUid, 0))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_QUANTIDADE_INVALIDA);

        assertThatThrownBy(() -> service.alterarQuantidade(carrinho, "uid-inexistente", 2))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_ITEM_NAO_ENCONTRADO);

        assertThatThrownBy(() -> service.alterarQuantidade(carrinho, primeiroUid, 5))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_ESTOQUE_INSUFICIENTE);
    }

    @Test
    void removerItemDeveExcluirItemExistenteEFalharQuandoNaoEncontrado() {
        when(repositorio.buscarProdutoDisponivel(1)).thenReturn(Optional.of(xBurger));
        service.adicionarItem(carrinho, 1, 1, List.of(), null);
        String uid = carrinho.getItens().getFirst().uid();

        service.removerItem(carrinho, uid);
        assertThat(carrinho.estaVazio()).isTrue();

        assertThatThrownBy(() -> service.removerItem(carrinho, uid))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_ITEM_NAO_ENCONTRADO);

        assertThatThrownBy(() -> service.removerItem(carrinho, " "))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_ITEM_NAO_ENCONTRADO);
    }

    @Test
    void atualizarOpcoesEObservacaoDeveAlterarItemEFalharQuandoOpcaoOuItemInvalido() {
        when(repositorio.buscarProdutoDisponivel(1)).thenReturn(Optional.of(xBurger));
        when(repositorio.listarOpcoesAtivasPorProduto(1)).thenReturn(List.of(adicionalBacon, removerCebola));

        service.adicionarItem(carrinho, 1, 2, List.of(), null);
        String uid = carrinho.getItens().getFirst().uid();

        service.atualizarOpcoesEObservacao(carrinho, uid, List.of(10), "  Cortar ao meio  ");
        ItemCarrinho atualizado = carrinho.getItens().getFirst();
        assertThat(atualizado.opcoes()).hasSize(1);
        assertThat(atualizado.observacao()).isEqualTo("Cortar ao meio");
        assertThat(atualizado.subtotal()).isEqualByComparingTo("59.00");

        assertThatThrownBy(() -> service.atualizarOpcoesEObservacao(carrinho, "nao-existe", List.of(), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_ITEM_NAO_ENCONTRADO);

        assertThatThrownBy(() -> service.atualizarOpcoesEObservacao(carrinho, uid, List.of(777), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_OPCAO_INVALIDA);
    }

    @Test
    void definirClienteDeveVincularAtivoDesvincularQuandoNuloEFalharParaInexistente() {
        when(repositorio.buscarNomeClienteAtivo(5)).thenReturn(Optional.of("Carlos Souza"));

        service.definirCliente(carrinho, 5);
        assertThat(carrinho.getIdCliente()).isEqualTo(5);
        assertThat(carrinho.getNomeCliente()).isEqualTo("Carlos Souza");

        service.definirCliente(carrinho, null);
        assertThat(carrinho.getIdCliente()).isNull();
        assertThat(carrinho.getNomeCliente()).isNull();

        assertThatThrownBy(() -> service.definirCliente(carrinho, 0))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_CLIENTE_INVALIDO);

        when(repositorio.buscarNomeClienteAtivo(88)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.definirCliente(carrinho, 88))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_CLIENTE_INVALIDO);
    }

    @Test
    void definirDadosFinalizacaoDeveValidarPagamentoEObservacao() {
        service.definirDadosFinalizacao(carrinho, FormaPagamento.CREDITO, "  Para viagem ");
        assertThat(carrinho.getFormaPagamento()).isEqualTo(FormaPagamento.CREDITO);
        assertThat(carrinho.getObservacao()).isEqualTo("Para viagem");

        assertThatThrownBy(() -> service.definirDadosFinalizacao(carrinho, null, null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_PAGAMENTO_OBRIGATORIO);

        String obsLonga = "x".repeat(256);
        assertThatThrownBy(() -> service.definirDadosFinalizacao(carrinho, FormaPagamento.PIX, obsLonga))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_OBSERVACAO_LONGA);
    }

    @Test
    void limparCarrinhoDeveRemoverItensEDadosEFalharQuandoCarrinhoNulo() {
        when(repositorio.buscarProdutoDisponivel(1)).thenReturn(Optional.of(xBurger));
        when(repositorio.buscarNomeClienteAtivo(5)).thenReturn(Optional.of("Carlos"));
        service.adicionarItem(carrinho, 1, 1, List.of(), "Obs");
        service.definirCliente(carrinho, 5);
        service.definirDadosFinalizacao(carrinho, FormaPagamento.DINHEIRO, "Troco");

        service.limparCarrinho(carrinho);

        assertThat(carrinho.estaVazio()).isTrue();
        assertThat(carrinho.getIdCliente()).isNull();
        assertThat(carrinho.getNomeCliente()).isNull();
        assertThat(carrinho.getFormaPagamento()).isNull();
        assertThat(carrinho.getObservacao()).isNull();

        verify(repositorio).buscarNomeClienteAtivo(5);

        assertThatThrownBy(() -> service.limparCarrinho(null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoCarrinhoService.MENSAGEM_CARRINHO_INVALIDO);
    }
}
