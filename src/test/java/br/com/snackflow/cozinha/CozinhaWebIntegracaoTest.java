package br.com.snackflow.cozinha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.snackflow.TestcontainersConfiguration;
import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Testes Web/Seguranca/Integracao do KDS (/cozinha) com vw_cozinha e sp_alterar_status_pedido no MySQL 8.4.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CozinhaWebIntegracaoTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    void deveExigirLoginPerfilCozinhaOuAdminECsrfEmCozinha() throws Exception {
        mvc.perform(get("/cozinha"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        UsuarioLogado cozinha = usuario(3, "marcos.cozinha", Perfil.COZINHA);
        UsuarioLogado admin = usuario(1, "admin", Perfil.ADMIN);
        UsuarioLogado caixa = usuario(2, "ana.caixa", Perfil.CAIXA);
        UsuarioLogado estoque = usuario(4, "julia.estoque", Perfil.ESTOQUE);

        mvc.perform(get("/cozinha").with(user(cozinha)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fila de preparo em tempo real")))
                .andExpect(content().string(containsString("document.hidden")));

        mvc.perform(get("/cozinha").with(user(admin)))
                .andExpect(status().isOk());

        mvc.perform(get("/cozinha").with(user(caixa)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/cozinha").with(user(estoque)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/cozinha/pedidos/1/pronto").with(user(cozinha)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveExibirOpcoesColoridasMarcarProntoERecusarCancelamentoPorCozinhaNoBanco() throws Exception {
        String sufixo = "coz-it-" + System.nanoTime();
        Integer idCat = inserir("INSERT INTO categorias (nome) VALUES (?)", "Cat " + sufixo);
        Integer idPao = inserir(
                "INSERT INTO ingredientes (nome, unidade, estoque_minimo, saldo_atual, custo_atual, ativo) "
                        + "VALUES (?, 'UN', 2, 10.000, 1.50, 1)",
                "Pao " + sufixo);
        Integer idBacon = inserir(
                "INSERT INTO ingredientes (nome, unidade, estoque_minimo, saldo_atual, custo_atual, ativo) "
                        + "VALUES (?, 'UN', 2, 10.000, 2.00, 1)",
                "Bacon " + sufixo);
        Integer idCebola = inserir(
                "INSERT INTO ingredientes (nome, unidade, estoque_minimo, saldo_atual, custo_atual, ativo) "
                        + "VALUES (?, 'UN', 2, 10.000, 0.50, 1)",
                "Cebola " + sufixo);

        Integer idProd = inserir(
                "INSERT INTO produtos (nome, id_categoria, preco, ativo) VALUES (?, ?, 25.00, 1)",
                "Burger " + sufixo, idCat);
        jdbc.update("INSERT INTO produto_ingredientes (id_produto, id_ingrediente, quantidade) VALUES (?, ?, 1.000)",
                idProd, idPao);
        jdbc.update("INSERT INTO produto_ingredientes (id_produto, id_ingrediente, quantidade) VALUES (?, ?, 1.000)",
                idProd, idCebola);

        Integer idAdic = inserir(
                "INSERT INTO produto_opcoes (id_produto, tipo, nome, id_ingrediente, quantidade, preco_adicional, ativo) "
                        + "VALUES (?, 'ADICIONAL', 'Bacon', ?, 1.000, 4.00, 1)",
                idProd, idBacon);
        Integer idRem = inserir(
                "INSERT INTO produto_opcoes (id_produto, tipo, nome, id_ingrediente, quantidade, preco_adicional, ativo) "
                        + "VALUES (?, 'REMOVER', 'Cebola', ?, NULL, 0.00, 1)",
                idProd, idCebola);

        Integer idCaixa = inserir(
                "INSERT INTO funcionarios (nome, cargo, perfil, login, senha_hash, ativo) "
                        + "VALUES (?, 'Caixa', 'CAIXA', ?, '$2a$10$hash', 1)",
                "Caixa " + sufixo, "cx." + sufixo);
        Integer idCozinhaTemp = inserir(
                "INSERT INTO funcionarios (nome, cargo, perfil, login, senha_hash, ativo) "
                        + "VALUES (?, 'Chapeiro', 'COZINHA', ?, '$2a$10$hash', 1)",
                "Chapeiro " + sufixo, "cz." + sufixo);

        try {
            Integer idPedido = inserir(
                    "INSERT INTO pedidos (id_funcionario, observacao) VALUES (?, ?)",
                    idCaixa, "Pedido " + sufixo);
            Integer idItem = inserir(
                    "INSERT INTO itens_pedido (id_pedido, id_produto, quantidade, observacao) VALUES (?, ?, 2, 'Ao ponto')",
                    idPedido, idProd);
            jdbc.update("INSERT INTO itens_pedido_opcoes (id_item, id_opcao) VALUES (?, ?), (?, ?)",
                    idItem, idAdic, idItem, idRem);
            jdbc.update("CALL sp_alterar_status_pedido(?, 'EM_PREPARO', 'PIX', ?)", idPedido, idCaixa);

            UsuarioLogado cozinha = usuario(idCozinhaTemp, "cz." + sufixo, Perfil.COZINHA);

            String htmlCozinha = mvc.perform(get("/cozinha").with(user(cozinha)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("#" + idPedido)))
                    .andExpect(content().string(containsString("+ Bacon")))
                    .andExpect(content().string(containsString("SEM Cebola")))
                    .andExpect(content().string(containsString("sf-badge-aviso")))
                    .andExpect(content().string(containsString("sf-badge-perigo")))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(htmlCozinha.split("Prioridade", -1).length - 1).isEqualTo(1);

            // Regra do banco: funcionario COZINHA tentando cancelar deve ser recusado
            assertThatThrownBy(() -> jdbc.update(
                    "CALL sp_alterar_status_pedido(?, 'CANCELADO', NULL, ?)", idPedido, idCozinhaTemp))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("A cozinha so pode marcar o pedido como PRONTO");

            // Marca como PRONTO pela tela da cozinha
            mvc.perform(post("/cozinha/pedidos/{id}/pronto", idPedido)
                            .with(user(cozinha))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/cozinha"))
                    .andExpect(flash().attributeExists("ok"));

            assertThat(jdbc.queryForObject("SELECT status FROM pedidos WHERE id_pedido = ?", String.class, idPedido))
                    .isEqualTo("PRONTO");

            // Depois de entregue pelo caixa, cozinha nao pode voltar o pedido para PRONTO
            jdbc.update("CALL sp_alterar_status_pedido(?, 'ENTREGUE', NULL, ?)", idPedido, idCaixa);
            mvc.perform(post("/cozinha/pedidos/{id}/pronto", idPedido)
                            .with(user(cozinha))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/cozinha"))
                    .andExpect(flash().attribute("erro", "Mudanca de status invalida para este pedido"));
        } finally {
            jdbc.update("DELETE FROM funcionarios WHERE id_funcionario = ?", idCozinhaTemp);
        }
    }

    private static UsuarioLogado usuario(Integer id, String login, Perfil perfil) {
        return new UsuarioLogado(id, login, login, "hash", perfil, true);
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
}
