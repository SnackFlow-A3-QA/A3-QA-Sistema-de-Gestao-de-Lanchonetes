package br.com.snackflow.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class PedidoRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private PedidoRepository repositorio;

    @Test
    @SuppressWarnings("unchecked")
    void deveMapearProdutosAtivosDaView() throws Exception {
        ArgumentCaptor<RowMapper<ProdutoDisponivel>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(anyString(), captor.capture())).thenReturn(List.of());

        repositorio.listarProdutosAtivos();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id_produto")).thenReturn(5);
        when(rs.getString("nome")).thenReturn("X-Bacon");
        when(rs.getString("categoria")).thenReturn("Lanches");
        when(rs.getBigDecimal("preco")).thenReturn(new BigDecimal("28.90"));
        when(rs.getBoolean("ativo")).thenReturn(true);
        when(rs.getInt("disponivel")).thenReturn(12);

        ProdutoDisponivel produto = captor.getValue().mapRow(rs, 0);
        assertThat(produto).isNotNull();
        assertThat(produto.idProduto()).isEqualTo(5);
        assertThat(produto.nome()).isEqualTo("X-Bacon");
        assertThat(produto.categoria()).isEqualTo("Lanches");
        assertThat(produto.preco()).isEqualByComparingTo("28.90");
        assertThat(produto.estaDisponivel()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveBuscarProdutoDisponivelPorIdOuVazio() {
        ProdutoDisponivel esperado = new ProdutoDisponivel(3, "Suco", "Bebidas", new BigDecimal("9.00"), true, 0);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(3))).thenReturn(List.of(esperado));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(99))).thenReturn(List.of());

        assertThat(repositorio.buscarProdutoDisponivel(3)).contains(esperado);
        assertThat(esperado.estaDisponivel()).isFalse();
        assertThat(repositorio.buscarProdutoDisponivel(99)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveMapearOpcoesAtivasDoProduto() throws Exception {
        ArgumentCaptor<RowMapper<OpcaoProduto>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(anyString(), captor.capture(), eq(5))).thenReturn(List.of());

        repositorio.listarOpcoesAtivasPorProduto(5);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id_opcao")).thenReturn(8);
        when(rs.getInt("id_produto")).thenReturn(5);
        when(rs.getString("tipo")).thenReturn("ADICIONAL");
        when(rs.getString("nome")).thenReturn("Ovo");
        when(rs.getBigDecimal("preco_adicional")).thenReturn(new BigDecimal("3.00"));
        when(rs.getBoolean("ativo")).thenReturn(true);

        OpcaoProduto opcao = captor.getValue().mapRow(rs, 0);
        assertThat(opcao).isNotNull();
        assertThat(opcao.ehAdicional()).isTrue();
        assertThat(opcao.precoAdicional()).isEqualByComparingTo("3.00");

        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(opcao));
        assertThat(repositorio.listarTodasOpcoesAtivas()).containsExactly(opcao);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveMapearPedidosEmAndamentoEStatusENomeCliente() throws Exception {
        ArgumentCaptor<RowMapper<PedidoEmAndamento>> pedidoCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(anyString(), pedidoCaptor.capture())).thenReturn(List.of());

        repositorio.listarPedidosEmAndamento();

        LocalDateTime agora = LocalDateTime.of(2026, 11, 27, 18, 0);
        ResultSet rsPedido = mock(ResultSet.class);
        when(rsPedido.getInt("id_pedido")).thenReturn(42);
        when(rsPedido.getTimestamp("data_hora")).thenReturn(Timestamp.valueOf(agora));
        when(rsPedido.getString("cliente")).thenReturn("Joao");
        when(rsPedido.getString("status")).thenReturn("PRONTO");
        when(rsPedido.getString("forma_pagamento")).thenReturn("PIX");
        when(rsPedido.getBigDecimal("valor_total")).thenReturn(new BigDecimal("45.00"));
        when(rsPedido.getString("resumo_itens")).thenReturn("2x X-Burger");

        PedidoEmAndamento pedido = pedidoCaptor.getValue().mapRow(rsPedido, 0);
        assertThat(pedido).isNotNull();
        assertThat(pedido.idPedido()).isEqualTo(42);
        assertThat(pedido.dataHora()).isEqualTo(agora);
        assertThat(pedido.status()).isEqualTo(StatusPedido.PRONTO);
        assertThat(pedido.formaPagamento()).isEqualTo(FormaPagamento.PIX);

        ResultSet rsSemOpcionais = mock(ResultSet.class);
        when(rsSemOpcionais.getInt("id_pedido")).thenReturn(43);
        when(rsSemOpcionais.getTimestamp("data_hora")).thenReturn(null);
        when(rsSemOpcionais.getString("cliente")).thenReturn(null);
        when(rsSemOpcionais.getString("status")).thenReturn("EM_PREPARO");
        when(rsSemOpcionais.getString("forma_pagamento")).thenReturn(null);
        when(rsSemOpcionais.getBigDecimal("valor_total")).thenReturn(BigDecimal.ZERO);
        when(rsSemOpcionais.getString("resumo_itens")).thenReturn(null);

        PedidoEmAndamento semOpcionais = pedidoCaptor.getValue().mapRow(rsSemOpcionais, 1);
        assertThat(semOpcionais.dataHora()).isNull();
        assertThat(semOpcionais.formaPagamento()).isNull();

        when(jdbc.query(anyString(), any(RowMapper.class), eq(42))).thenReturn(List.of(StatusPedido.PRONTO));
        assertThat(repositorio.buscarStatusPedido(42)).contains(StatusPedido.PRONTO);

        when(jdbc.query(anyString(), any(RowMapper.class), eq(9))).thenReturn(List.of("Maria"));
        Optional<String> nome = repositorio.buscarNomeClienteAtivo(9);
        assertThat(nome).contains("Maria");
    }
}
