package br.com.snackflow.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.snackflow.TestcontainersConfiguration;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Integracao de leitura do modulo pedido contra views e tabelas reais no MySQL 8.4.
 * Usa dados proprios e @Transactional (sem chamar a procedure) para reverter ao final.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PedidoRepositoryIntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PedidoRepository repositorio;

    @Test
    void deveLerDisponibilidadeDaViewEOpcoesEClienteAtivosNoMysql() {
        String sufixo = "it-ped-" + System.nanoTime();
        Integer idCategoria = inserirERetornarId(
                "INSERT INTO categorias (nome) VALUES (?)", "Cat " + sufixo);
        Integer idPao = inserirERetornarId(
                "INSERT INTO ingredientes (nome, unidade, estoque_minimo, saldo_atual, custo_atual, ativo) "
                        + "VALUES (?, 'UN', 2, 7, 1.50, 1)",
                "Pao " + sufixo);
        Integer idQueijo = inserirERetornarId(
                "INSERT INTO ingredientes (nome, unidade, estoque_minimo, saldo_atual, custo_atual, ativo) "
                        + "VALUES (?, 'UN', 2, 0, 2.00, 1)",
                "Queijo " + sufixo);

        Integer idDisponivel = inserirERetornarId(
                "INSERT INTO produtos (nome, id_categoria, preco, ativo) VALUES (?, ?, 22.50, 1)",
                "Lanche Disp " + sufixo, idCategoria);
        Integer idEsgotado = inserirERetornarId(
                "INSERT INTO produtos (nome, id_categoria, preco, ativo) VALUES (?, ?, 19.00, 1)",
                "Lanche Zerado " + sufixo, idCategoria);
        Integer idInativo = inserirERetornarId(
                "INSERT INTO produtos (nome, id_categoria, preco, ativo) VALUES (?, ?, 15.00, 0)",
                "Lanche Inativo " + sufixo, idCategoria);

        jdbc.update("INSERT INTO produto_ingredientes (id_produto, id_ingrediente, quantidade) VALUES (?, ?, 2)",
                idDisponivel, idPao);
        jdbc.update("INSERT INTO produto_ingredientes (id_produto, id_ingrediente, quantidade) VALUES (?, ?, 1)",
                idEsgotado, idQueijo);
        jdbc.update("INSERT INTO produto_ingredientes (id_produto, id_ingrediente, quantidade) VALUES (?, ?, 1)",
                idInativo, idPao);

        jdbc.update("INSERT INTO produto_opcoes (id_produto, tipo, nome, id_ingrediente, quantidade, preco_adicional, ativo) "
                        + "VALUES (?, 'ADICIONAL', 'Queijo Extra', ?, 1, 3.50, 1)",
                idDisponivel, idQueijo);
        jdbc.update("INSERT INTO produto_opcoes (id_produto, tipo, nome, id_ingrediente, quantidade, preco_adicional, ativo) "
                        + "VALUES (?, 'REMOVER', 'Sem Pao', ?, NULL, 0.00, 0)",
                idDisponivel, idPao);

        Integer idClienteAtivo = inserirERetornarId(
                "INSERT INTO clientes (nome, ativo) VALUES (?, 1)", "Cliente Ativo " + sufixo);
        Integer idClienteInativo = inserirERetornarId(
                "INSERT INTO clientes (nome, ativo) VALUES (?, 0)", "Cliente Inativo " + sufixo);

        List<ProdutoDisponivel> ativos = repositorio.listarProdutosAtivos();
        assertThat(ativos).extracting(ProdutoDisponivel::idProduto)
                .contains(idDisponivel, idEsgotado)
                .doesNotContain(idInativo);

        ProdutoDisponivel lido = repositorio.buscarProdutoDisponivel(idDisponivel).orElseThrow();
        assertThat(lido.disponivel()).isEqualTo(3);
        assertThat(lido.preco()).isEqualByComparingTo(new BigDecimal("22.50"));
        assertThat(lido.estaDisponivel()).isTrue();

        ProdutoDisponivel zerado = repositorio.buscarProdutoDisponivel(idEsgotado).orElseThrow();
        assertThat(zerado.disponivel()).isZero();
        assertThat(zerado.estaDisponivel()).isFalse();

        List<OpcaoProduto> opcoes = repositorio.listarOpcoesAtivasPorProduto(idDisponivel);
        assertThat(opcoes).hasSize(1);
        assertThat(opcoes.getFirst().nome()).isEqualTo("Queijo Extra");
        assertThat(opcoes.getFirst().ehAdicional()).isTrue();

        assertThat(repositorio.buscarNomeClienteAtivo(idClienteAtivo)).contains("Cliente Ativo " + sufixo);
        assertThat(repositorio.buscarNomeClienteAtivo(idClienteInativo)).isEmpty();
    }

    private Integer inserirERetornarId(String sql, Object... args) {
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
}
