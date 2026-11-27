package br.com.snackflow.cozinha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.snackflow.common.RegraNegocioException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class CozinhaServiceTest {

    @Mock
    private CozinhaRepository repositorio;

    @InjectMocks
    private CozinhaService service;

    @Test
    void listarFilaOrdenadaDeveRetornarListaVaziaQuandoSemPedidos() {
        when(repositorio.listarLinhasFila()).thenReturn(List.of());
        assertThat(service.listarFilaOrdenada()).isEmpty();
    }

    @Test
    void listarFilaOrdenadaDeveAgruparReordenarDoMaisAntigoAoMaisNovoEDestacarPrimeiro() {
        LocalDateTime maisNovo = LocalDateTime.of(2026, 11, 27, 19, 10);
        LocalDateTime maisAntigo = LocalDateTime.of(2026, 11, 27, 19, 0);

        when(repositorio.listarLinhasFila()).thenReturn(List.of(
                new LinhaCozinha(30, maisNovo, "Lucas", null, "Suco", 1, null, null),
                new LinhaCozinha(10, maisAntigo, "Ana", "Para viagem", "X-Burger", 2, "+ Bacon, SEM Cebola", "Ao ponto"),
                new LinhaCozinha(10, maisAntigo, "Ana", "Para viagem", "Fritas", 1, "SEM Sal", null),
                new LinhaCozinha(20, maisAntigo, null, null, "Refrigerante", 1, "+ Limao", null)));

        List<PedidoCozinha> fila = service.listarFilaOrdenada();

        assertThat(fila).extracting(PedidoCozinha::idPedido).containsExactly(10, 20, 30);
        assertThat(fila.get(0).prioritario()).isTrue();
        assertThat(fila.get(1).prioritario()).isFalse();
        assertThat(fila.get(2).prioritario()).isFalse();

        PedidoCozinha primeiro = fila.getFirst();
        assertThat(primeiro.itens()).hasSize(2);
        ItemPedidoCozinha lanche = primeiro.itens().getFirst();
        assertThat(lanche.opcoes()).containsExactly(
                new OpcaoItemCozinha(true, "+ Bacon"),
                new OpcaoItemCozinha(false, "SEM Cebola"));
    }

    @Test
    void marcarComoProntoDeveChamarRepositorioNoSucessoEFalharParaEntradasOuErroDoBanco() {
        service.marcarComoPronto(10, 3);
        verify(repositorio).marcarPedidoComoPronto(10, 3);

        assertThatThrownBy(() -> service.marcarComoPronto(0, 3))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(CozinhaService.MENSAGEM_PEDIDO_INVALIDO);

        assertThatThrownBy(() -> service.marcarComoPronto(10, null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(CozinhaService.MENSAGEM_FUNCIONARIO_INVALIDO);

        doThrow(new DataAccessResourceFailureException(
                "proc", new SQLException("Mudanca de status invalida para este pedido", "45000")))
                .when(repositorio).marcarPedidoComoPronto(99, 3);

        assertThatThrownBy(() -> service.marcarComoPronto(99, 3))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Mudanca de status invalida para este pedido");
    }
}
