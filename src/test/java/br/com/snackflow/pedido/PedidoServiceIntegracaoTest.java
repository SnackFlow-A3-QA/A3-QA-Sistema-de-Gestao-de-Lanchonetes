package br.com.snackflow.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.snackflow.TestcontainersConfiguration;
import br.com.snackflow.common.RegraNegocioException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Testes de integracao da confirmacao em duas etapas, entrega e cancelamento no MySQL 8.4 real.
 * NAO usa @Transactional porque sp_alterar_status_pedido gerencia transacoes proprias.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PedidoServiceIntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PedidoCarrinhoService carrinhoService;

    @Autowired
    private PedidoService pedidoService;

    @Test
    void deveConfirmarPedidoEmDuasEtapasBaixarEstoqueECalcularValorTotalPeloBanco() {
        CenarioFixture f = criarCenario("it-ok-" + System.nanoTime(), new BigDecimal("10.000"));

        CarrinhoPedido carrinho = new CarrinhoPedido();
        carrinhoService.adicionarItem(carrinho, f.idProduto(), 2, List.of(f.idAdicional(), f.idRemover()), "Ao ponto");
        carrinhoService.definirDadosFinalizacao(carrinho, FormaPagamento.PIX, "Entrega rapida");

        Integer idPedido = pedidoService.confirmarPedido(carrinho, f.idCaixa());

        assertThat(carrinho.estaVazio()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM pedidos WHERE id_pedido = ?", String.class, idPedido))
                .isEqualTo("EM_PREPARO");
        assertThat(jdbc.queryForObject("SELECT valor_total FROM pedidos WHERE id_pedido = ?", BigDecimal.class, idPedido))
                .isEqualByComparingTo("49.00");
        assertThat(jdbc.queryForObject("SELECT saldo_atual FROM ingredientes WHERE id_ingrediente = ?", BigDecimal.class, f.idPao()))
                .isEqualByComparingTo("6.000");
        assertThat(jdbc.queryForObject("SELECT saldo_atual FROM ingredientes WHERE id_ingrediente = ?", BigDecimal.class, f.idQueijo()))
                .isEqualByComparingTo("8.000");
    }

    @Test
    void deveCancelarPedidoAbertoEManterCarrinhoQuandoProcedureFalharPorEstoqueInsuficiente() {
        CenarioFixture f = criarCenario("it-est-" + System.nanoTime(), new BigDecimal("6.000"));

        CarrinhoPedido carrinho = new CarrinhoPedido();
        carrinhoService.adicionarItem(carrinho, f.idProduto(), 2, List.of(), "Sem pimenta");
        carrinhoService.definirDadosFinalizacao(carrinho, FormaPagamento.CREDITO, "Mesa 3");

        // Simula consumo concorrente antes da confirmacao para a procedure falhar na etapa 2
        jdbc.update("UPDATE ingredientes SET saldo_atual = 1.000 WHERE id_ingrediente = ?", f.idPao());

        Integer ultimoPedidoAntes = jdbc.queryForObject("SELECT COALESCE(MAX(id_pedido), 0) FROM pedidos", Integer.class);

        assertThatThrownBy(() -> pedidoService.confirmarPedido(carrinho, f.idCaixa()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Estoque insuficiente");

        assertThat(carrinho.estaVazio()).isFalse();
        assertThat(carrinho.getItens()).hasSize(1);
        assertThat(carrinho.getFormaPagamento()).isEqualTo(FormaPagamento.CREDITO);

        String statusGerado = jdbc.queryForObject(
                "SELECT status FROM pedidos WHERE id_pedido > ? AND id_funcionario = ? ORDER BY id_pedido DESC LIMIT 1",
                String.class,
                ultimoPedidoAntes,
                f.idCaixa());
        assertThat(statusGerado).isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("SELECT saldo_atual FROM ingredientes WHERE id_ingrediente = ?", BigDecimal.class, f.idPao()))
                .isEqualByComparingTo("1.000");
    }

    @Test
    void deveEntregarSomenteQuandoProntoECancelarEmPreparoDevolvendoEstoque() {
        CenarioFixture f = criarCenario("it-fluxo-" + System.nanoTime(), new BigDecimal("10.000"));

        CarrinhoPedido c1 = new CarrinhoPedido();
        carrinhoService.adicionarItem(c1, f.idProduto(), 1, List.of(), null);
        carrinhoService.definirDadosFinalizacao(c1, FormaPagamento.DEBITO, null);
        Integer idParaEntrega = pedidoService.confirmarPedido(c1, f.idCaixa());

        assertThatThrownBy(() -> pedidoService.entregarPedido(idParaEntrega, f.idCaixa()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(PedidoService.MENSAGEM_SO_ENTREGA_PRONTO);

        jdbc.update("CALL sp_alterar_status_pedido(?, 'PRONTO', NULL, ?)", idParaEntrega, f.idCozinha());
        pedidoService.entregarPedido(idParaEntrega, f.idCaixa());
        assertThat(jdbc.queryForObject("SELECT status FROM pedidos WHERE id_pedido = ?", String.class, idParaEntrega))
                .isEqualTo("ENTREGUE");

        BigDecimal saldoAntesSegundo = jdbc.queryForObject(
                "SELECT saldo_atual FROM ingredientes WHERE id_ingrediente = ?", BigDecimal.class, f.idPao());

        CarrinhoPedido c2 = new CarrinhoPedido();
        carrinhoService.adicionarItem(c2, f.idProduto(), 1, List.of(), null);
        carrinhoService.definirDadosFinalizacao(c2, FormaPagamento.DINHEIRO, null);
        Integer idParaCancelar = pedidoService.confirmarPedido(c2, f.idCaixa());

        pedidoService.cancelarPedido(idParaCancelar, f.idCaixa());
        assertThat(jdbc.queryForObject("SELECT status FROM pedidos WHERE id_pedido = ?", String.class, idParaCancelar))
                .isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("SELECT saldo_atual FROM ingredientes WHERE id_ingrediente = ?", BigDecimal.class, f.idPao()))
                .isEqualByComparingTo(saldoAntesSegundo);
    }

    private CenarioFixture criarCenario(String sufixo, BigDecimal saldoInicial) {
        Integer idCategoria = inserir(
                "INSERT INTO categorias (nome) VALUES (?)", "Cat " + sufixo);
        Integer idPao = inserir(
                "INSERT INTO ingredientes (nome, unidade, estoque_minimo, saldo_atual, custo_atual, ativo) "
                        + "VALUES (?, 'UN', 2, ?, 1.50, 1)",
                "Pao " + sufixo, saldoInicial);
        Integer idQueijo = inserir(
                "INSERT INTO ingredientes (nome, unidade, estoque_minimo, saldo_atual, custo_atual, ativo) "
                        + "VALUES (?, 'UN', 2, ?, 2.00, 1)",
                "Queijo " + sufixo, saldoInicial);
        Integer idCebola = inserir(
                "INSERT INTO ingredientes (nome, unidade, estoque_minimo, saldo_atual, custo_atual, ativo) "
                        + "VALUES (?, 'UN', 2, ?, 0.50, 1)",
                "Cebola " + sufixo, saldoInicial);

        Integer idProduto = inserir(
                "INSERT INTO produtos (nome, id_categoria, preco, ativo) VALUES (?, ?, 20.00, 1)",
                "Lanche " + sufixo, idCategoria);
        jdbc.update("INSERT INTO produto_ingredientes (id_produto, id_ingrediente, quantidade) VALUES (?, ?, 2.000)",
                idProduto, idPao);
        jdbc.update("INSERT INTO produto_ingredientes (id_produto, id_ingrediente, quantidade) VALUES (?, ?, 1.000)",
                idProduto, idCebola);

        Integer idAdicional = inserir(
                "INSERT INTO produto_opcoes (id_produto, tipo, nome, id_ingrediente, quantidade, preco_adicional, ativo) "
                        + "VALUES (?, 'ADICIONAL', 'Queijo Extra', ?, 1.000, 4.50, 1)",
                idProduto, idQueijo);
        Integer idRemover = inserir(
                "INSERT INTO produto_opcoes (id_produto, tipo, nome, id_ingrediente, quantidade, preco_adicional, ativo) "
                        + "VALUES (?, 'REMOVER', 'Cebola', ?, NULL, 0.00, 1)",
                idProduto, idCebola);

        Integer idCaixa = inserir(
                "INSERT INTO funcionarios (nome, cargo, perfil, login, senha_hash, ativo) "
                        + "VALUES (?, 'Caixa', 'CAIXA', ?, '$2a$10$hash', 1)",
                "Caixa " + sufixo, "cx." + sufixo);
        Integer idCozinha = inserir(
                "INSERT INTO funcionarios (nome, cargo, perfil, login, senha_hash, ativo) "
                        + "VALUES (?, 'Chapeiro', 'COZINHA', ?, '$2a$10$hash', 1)",
                "Cozinha " + sufixo, "cz." + sufixo);

        return new CenarioFixture(idProduto, idPao, idQueijo, idAdicional, idRemover, idCaixa, idCozinha);
    }

    private Integer inserir(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    private record CenarioFixture(
            Integer idProduto,
            Integer idPao,
            Integer idQueijo,
            Integer idAdicional,
            Integer idRemover,
            Integer idCaixa,
            Integer idCozinha) {
    }
}
