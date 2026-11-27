package br.com.snackflow.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.snackflow.common.RegraNegocioException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository repositorio;

    @InjectMocks
    private PedidoService service;

    private CarrinhoPedido carrinho;

    @BeforeEach
    void configurar() {
        carrinho = new CarrinhoPedido();
        carrinho.adicionarItem(new ItemCarrinho(
                "uid-1",
                1,
                "X-Burger",
                new BigDecimal("25.00"),
                2,
                List.of(new OpcaoCarrinho(10, "ADICIONAL", "Bacon", new BigDecimal("4.50"))),
                "Sem cebola"));
        carrinho.definirFormaPagamento(FormaPagamento.PIX);
        carrinho.definirObservacao("Para viagem");
    }

    @Test
    void listarPedidosEmAndamentoDeveDelegarAoRepositorio() {
        PedidoEmAndamento p = new PedidoEmAndamento(
                5, LocalDateTime.now(), "Ana", StatusPedido.PRONTO, FormaPagamento.PIX, BigDecimal.TEN, "1x X-Burger");
        when(repositorio.listarPedidosEmAndamento()).thenReturn(List.of(p));

        assertThat(service.listarPedidosEmAndamento()).containsExactly(p);

        when(repositorio.listarPedidosEmAndamento()).thenReturn(List.of());
        assertThat(service.listarPedidosEmAndamento()).isEmpty();
    }

    @Test
    void confirmarPedidoDeveExecutarDuasEtapasELimparCarrinhoNoSucesso() {
        when(repositorio.gravarPedidoAberto(null, 2, "Para viagem", carrinho.getItens())).thenReturn(77);

        Integer idPedido = service.confirmarPedido(carrinho, 2);

        assertThat(idPedido).isEqualTo(77);
        assertThat(carrinho.estaVazio()).isTrue();
        assertThat(carrinho.getFormaPagamento()).isNull();

        InOrder ordem = inOrder(repositorio);
        ordem.verify(repositorio).gravarPedidoAberto(null, 2, "Para viagem", List.of(
                new ItemCarrinho("uid-1", 1, "X-Burger", new BigDecimal("25.00"), 2,
                        List.of(new OpcaoCarrinho(10, "ADICIONAL", "Bacon", new BigDecimal("4.50"))),
                        "Sem cebola")));
        ordem.verify(repositorio).alterarStatusPedido(77, StatusPedido.EM_PREPARO, FormaPagamento.PIX, 2);
    }

    @Test
    void confirmarPedidoDeveCancelarRascunhoEManterCarrinhoComMensagemOriginalSeProcedureFalhar() {
        when(repositorio.gravarPedidoAberto(null, 2, "Para viagem", carrinho.getItens())).thenReturn(88);

        DataAccessResourceFailureException erroBanco = new DataAccessResourceFailureException(
                "erro", new SQLException("Estoque insuficiente (ingrediente 1): saldo 1.000, solicitado 2.000", "45000"));
        doThrow(erroBanco).when(repositorio)
                .alterarStatusPedido(88, StatusPedido.EM_PREPARO, FormaPagamento.PIX, 2);
        doNothing().when(repositorio).alterarStatusPedido(88, StatusPedido.CANCELADO, null, 2);

        assertThatThrownBy(() -> service.confirmarPedido(carrinho, 2))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Estoque insuficiente (ingrediente 1): saldo 1.000, solicitado 2.000");

        verify(repositorio).alterarStatusPedido(88, StatusPedido.CANCELADO, null, 2);
        assertThat(carrinho.estaVazio()).isFalse();
        assertThat(carrinho.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
    }

    @Test
    void confirmarPedidoDeveManterMensagemOriginalQuandoLimpezaDoRascunhoTambemFalhar() {
        when(repositorio.gravarPedidoAberto(any(), eq(2), any(), any())).thenReturn(89);

        DataAccessResourceFailureException erroOriginal = new DataAccessResourceFailureException(
                "erro", new SQLException("Saldo insuficiente", "45000"));
        doThrow(erroOriginal).when(repositorio)
                .alterarStatusPedido(89, StatusPedido.EM_PREPARO, FormaPagamento.PIX, 2);
        doThrow(new TransientDataAccessResourceException("limpeza", new SQLException("Deadlock", "40001")))
                .when(repositorio)
                .alterarStatusPedido(89, StatusPedido.CANCELADO, null, 2);

        assertThatThrownBy(() -> service.confirmarPedido(carrinho, 2))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Saldo insuficiente");

        assertThat(carrinho.estaVazio()).isFalse();
    }

    @Test
    void confirmarPedidoDeveFalharParaCarrinhoVazioSemPagamentoFuncionarioOuErroNaEtapa1() {
        CarrinhoPedido vazio = new CarrinhoPedido();
        assertThatThrownBy(() -> service.confirmarPedido(vazio, 2))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoService.MENSAGEM_CARRINHO_VAZIO);

        carrinho.definirFormaPagamento(null);
        assertThatThrownBy(() -> service.confirmarPedido(carrinho, 2))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoService.MENSAGEM_PAGAMENTO_OBRIGATORIO);

        carrinho.definirFormaPagamento(FormaPagamento.DINHEIRO);
        assertThatThrownBy(() -> service.confirmarPedido(carrinho, 0))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoService.MENSAGEM_FUNCIONARIO_INVALIDO);

        when(repositorio.gravarPedidoAberto(any(), eq(2), any(), any()))
                .thenThrow(new DataAccessResourceFailureException(
                        "trigger", new SQLException("Produto inativo nao pode ser vendido", "45000")));

        assertThatThrownBy(() -> service.confirmarPedido(carrinho, 2))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Produto inativo nao pode ser vendido");
        verify(repositorio, never()).alterarStatusPedido(any(), any(), any(), any());
    }

    @Test
    void entregarPedidoDevePermitirSomenteQuandoProntoEFalharCasoContrario() {
        when(repositorio.buscarStatusPedido(15)).thenReturn(Optional.of(StatusPedido.PRONTO));
        service.entregarPedido(15, 2);
        verify(repositorio).alterarStatusPedido(15, StatusPedido.ENTREGUE, null, 2);

        when(repositorio.buscarStatusPedido(16)).thenReturn(Optional.of(StatusPedido.EM_PREPARO));
        assertThatThrownBy(() -> service.entregarPedido(16, 2))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoService.MENSAGEM_SO_ENTREGA_PRONTO);

        when(repositorio.buscarStatusPedido(999)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.entregarPedido(999, 2))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoService.MENSAGEM_PEDIDO_NAO_ENCONTRADO);
    }

    @Test
    void cancelarPedidoDevePermitirSomenteEmPreparoEConverterErroDoBanco() {
        when(repositorio.buscarStatusPedido(20)).thenReturn(Optional.of(StatusPedido.EM_PREPARO));
        service.cancelarPedido(20, 2);
        verify(repositorio).alterarStatusPedido(20, StatusPedido.CANCELADO, null, 2);

        when(repositorio.buscarStatusPedido(21)).thenReturn(Optional.of(StatusPedido.PRONTO));
        assertThatThrownBy(() -> service.cancelarPedido(21, 2))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoService.MENSAGEM_SO_CANCELA_EM_PREPARO);

        when(repositorio.buscarStatusPedido(22)).thenReturn(Optional.of(StatusPedido.EM_PREPARO));
        doThrow(new DataAccessResourceFailureException(
                "proc", new SQLException("A cozinha so pode marcar o pedido como PRONTO", "45000")))
                .when(repositorio).alterarStatusPedido(22, StatusPedido.CANCELADO, null, 3);

        assertThatThrownBy(() -> service.cancelarPedido(22, 3))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A cozinha so pode marcar o pedido como PRONTO");
    }
}
