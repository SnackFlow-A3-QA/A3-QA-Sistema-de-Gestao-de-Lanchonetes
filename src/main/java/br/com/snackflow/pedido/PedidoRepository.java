package br.com.snackflow.pedido;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso a dados do modulo pedido via JdbcTemplate (views e tabelas do MySQL, sem JPA).
 */
@Repository
public class PedidoRepository {

    private static final String SQL_INSERIR_PEDIDO = """
            INSERT INTO pedidos (id_cliente, id_funcionario, observacao)
            VALUES (?, ?, ?)
            """;

    private static final String SQL_INSERIR_ITEM = """
            INSERT INTO itens_pedido (id_pedido, id_produto, quantidade, observacao)
            VALUES (?, ?, ?, ?)
            """;

    private static final String SQL_INSERIR_ITEM_OPCAO = """
            INSERT INTO itens_pedido_opcoes (id_item, id_opcao)
            VALUES (?, ?)
            """;

    private static final String SQL_ALTERAR_STATUS = "CALL sp_alterar_status_pedido(?, ?, ?, ?)";

    private static final String SQL_LISTAR_PRODUTOS_ATIVOS = """
            SELECT id_produto, nome, categoria, preco, ativo, disponivel
              FROM vw_disponibilidade_produtos
             WHERE ativo = 1
             ORDER BY categoria, nome
            """;

    private static final String SQL_BUSCAR_PRODUTO_POR_ID = """
            SELECT id_produto, nome, categoria, preco, ativo, disponivel
              FROM vw_disponibilidade_produtos
             WHERE id_produto = ?
            """;

    private static final String SQL_LISTAR_OPCOES_ATIVAS = """
            SELECT id_opcao, id_produto, tipo, nome, preco_adicional, ativo
              FROM produto_opcoes
             WHERE id_produto = ? AND ativo = 1
             ORDER BY tipo, nome
            """;

    private static final String SQL_LISTAR_TODAS_OPCOES_ATIVAS = """
            SELECT id_opcao, id_produto, tipo, nome, preco_adicional, ativo
              FROM produto_opcoes
             WHERE ativo = 1
             ORDER BY id_produto, tipo, nome
            """;

    private static final String SQL_BUSCAR_NOME_CLIENTE_ATIVO = """
            SELECT nome
              FROM clientes
             WHERE id_cliente = ? AND ativo = 1
            """;

    private static final String SQL_LISTAR_PEDIDOS_EM_ANDAMENTO = """
            SELECT p.id_pedido,
                   p.data_hora,
                   cl.nome AS cliente,
                   p.status,
                   p.forma_pagamento,
                   p.valor_total,
                   (SELECT GROUP_CONCAT(CONCAT(ip.quantidade, 'x ', pr.nome) ORDER BY ip.id_item SEPARATOR ', ')
                      FROM itens_pedido ip
                      JOIN produtos pr ON pr.id_produto = ip.id_produto
                     WHERE ip.id_pedido = p.id_pedido) AS resumo_itens
              FROM pedidos p
              LEFT JOIN clientes cl ON cl.id_cliente = p.id_cliente
             WHERE p.status IN ('EM_PREPARO', 'PRONTO')
             ORDER BY CASE p.status WHEN 'PRONTO' THEN 0 ELSE 1 END, p.data_hora, p.id_pedido
            """;

    private static final String SQL_BUSCAR_STATUS_PEDIDO = """
            SELECT status
              FROM pedidos
             WHERE id_pedido = ?
            """;

    private static final RowMapper<ProdutoDisponivel> MAPA_PRODUTO = PedidoRepository::mapearProduto;
    private static final RowMapper<OpcaoProduto> MAPA_OPCAO = PedidoRepository::mapearOpcao;
    private static final RowMapper<PedidoEmAndamento> MAPA_PEDIDO_EM_ANDAMENTO =
            PedidoRepository::mapearPedidoEmAndamento;

    private final JdbcTemplate jdbc;

    public PedidoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProdutoDisponivel> listarProdutosAtivos() {
        return jdbc.query(SQL_LISTAR_PRODUTOS_ATIVOS, MAPA_PRODUTO);
    }

    public Optional<ProdutoDisponivel> buscarProdutoDisponivel(Integer idProduto) {
        return jdbc.query(SQL_BUSCAR_PRODUTO_POR_ID, MAPA_PRODUTO, idProduto).stream().findFirst();
    }

    public List<OpcaoProduto> listarOpcoesAtivasPorProduto(Integer idProduto) {
        return jdbc.query(SQL_LISTAR_OPCOES_ATIVAS, MAPA_OPCAO, idProduto);
    }

    public List<OpcaoProduto> listarTodasOpcoesAtivas() {
        return jdbc.query(SQL_LISTAR_TODAS_OPCOES_ATIVAS, MAPA_OPCAO);
    }

    public Optional<String> buscarNomeClienteAtivo(Integer idCliente) {
        return jdbc.query(SQL_BUSCAR_NOME_CLIENTE_ATIVO, (rs, rowNum) -> rs.getString("nome"), idCliente)
                .stream()
                .findFirst();
    }

    public List<PedidoEmAndamento> listarPedidosEmAndamento() {
        return jdbc.query(SQL_LISTAR_PEDIDOS_EM_ANDAMENTO, MAPA_PEDIDO_EM_ANDAMENTO);
    }

    public Optional<StatusPedido> buscarStatusPedido(Integer idPedido) {
        return jdbc.query(SQL_BUSCAR_STATUS_PEDIDO,
                        (rs, rowNum) -> StatusPedido.valueOf(rs.getString("status")),
                        idPedido)
                .stream()
                .findFirst();
    }

    /** Etapa 1 da confirmacao: grava pedido ABERTO, itens e opcoes dentro de uma transacao. */
    @Transactional
    public Integer gravarPedidoAberto(
            Integer idCliente,
            Integer idFuncionario,
            String observacao,
            List<ItemCarrinho> itens) {
        Integer idPedido = inserirCabecalhoPedido(idCliente, idFuncionario, observacao);
        for (ItemCarrinho item : itens) {
            Integer idItem = inserirItemPedido(idPedido, item);
            for (OpcaoCarrinho opcao : item.opcoes()) {
                jdbc.update(SQL_INSERIR_ITEM_OPCAO, idItem, opcao.idOpcao());
            }
        }
        return idPedido;
    }

    /** Etapa 2 e transicoes de status: chama sp_alterar_status_pedido fora de transacao. */
    public void alterarStatusPedido(
            Integer idPedido,
            StatusPedido novoStatus,
            FormaPagamento formaPagamento,
            Integer idFuncionario) {
        jdbc.update(
                SQL_ALTERAR_STATUS,
                idPedido,
                novoStatus.name(),
                formaPagamento == null ? null : formaPagamento.name(),
                idFuncionario);
    }

    private Integer inserirCabecalhoPedido(Integer idCliente, Integer idFuncionario, String observacao) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERIR_PEDIDO, Statement.RETURN_GENERATED_KEYS);
            if (idCliente == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, idCliente);
            }
            ps.setInt(2, idFuncionario);
            if (observacao == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, observacao);
            }
            return ps;
        }, keyHolder);
        Number chave = keyHolder.getKey();
        if (chave == null) {
            throw new IllegalStateException("Banco nao retornou id_pedido gerado.");
        }
        return chave.intValue();
    }

    private Integer inserirItemPedido(Integer idPedido, ItemCarrinho item) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERIR_ITEM, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idPedido);
            ps.setInt(2, item.idProduto());
            ps.setInt(3, item.quantidade());
            if (item.observacao() == null) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, item.observacao());
            }
            return ps;
        }, keyHolder);
        Number chave = keyHolder.getKey();
        if (chave == null) {
            throw new IllegalStateException("Banco nao retornou id_item gerado.");
        }
        return chave.intValue();
    }

    private static ProdutoDisponivel mapearProduto(ResultSet rs, int rowNum) throws SQLException {
        return new ProdutoDisponivel(
                rs.getInt("id_produto"),
                rs.getString("nome"),
                rs.getString("categoria"),
                rs.getBigDecimal("preco"),
                rs.getBoolean("ativo"),
                rs.getInt("disponivel"));
    }

    private static OpcaoProduto mapearOpcao(ResultSet rs, int rowNum) throws SQLException {
        return new OpcaoProduto(
                rs.getInt("id_opcao"),
                rs.getInt("id_produto"),
                rs.getString("tipo"),
                rs.getString("nome"),
                rs.getBigDecimal("preco_adicional"),
                rs.getBoolean("ativo"));
    }

    private static PedidoEmAndamento mapearPedidoEmAndamento(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("data_hora");
        String formaTexto = rs.getString("forma_pagamento");
        return new PedidoEmAndamento(
                rs.getInt("id_pedido"),
                ts == null ? null : ts.toLocalDateTime(),
                rs.getString("cliente"),
                StatusPedido.valueOf(rs.getString("status")),
                formaTexto == null ? null : FormaPagamento.valueOf(formaTexto),
                rs.getBigDecimal("valor_total"),
                rs.getString("resumo_itens"));
    }
}
