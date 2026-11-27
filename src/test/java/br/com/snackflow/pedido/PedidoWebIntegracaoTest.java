package br.com.snackflow.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.snackflow.TestcontainersConfiguration;
import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Testes Web/Seguranca/Integracao do PDV (/pdv) com MySQL 8.4 real e sessao HTTP.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PedidoWebIntegracaoTest {

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
    void deveControlarAcessoPorPerfilEExigirLoginECsrfEmPdv() throws Exception {
        mvc.perform(get("/pdv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        UsuarioLogado caixa = usuario(2, "ana.caixa", Perfil.CAIXA);
        UsuarioLogado admin = usuario(1, "admin", Perfil.ADMIN);
        UsuarioLogado cozinha = usuario(3, "marcos.cozinha", Perfil.COZINHA);
        UsuarioLogado estoque = usuario(4, "julia.estoque", Perfil.ESTOQUE);

        mvc.perform(get("/pdv").with(user(caixa)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ponto de venda (PDV)")))
                .andExpect(content().string(containsString("Pedidos em andamento")));

        mvc.perform(get("/pdv").with(user(admin)))
                .andExpect(status().isOk());

        mvc.perform(get("/pdv").with(user(cozinha)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/pdv").with(user(estoque)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/pdv/confirmar").with(user(caixa)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveExecutarFluxoCompletoDoPdvNaSessaoComEntregaECancelamento() throws Exception {
        String sufixo = "web-pdv-" + System.nanoTime();
        Integer idCat = inserir("INSERT INTO categorias (nome) VALUES (?)", "Cat " + sufixo);
        Integer idIng = inserir(
                "INSERT INTO ingredientes (nome, unidade, estoque_minimo, saldo_atual, custo_atual, ativo) "
                        + "VALUES (?, 'UN', 2, 10.000, 2.00, 1)",
                "Ing " + sufixo);
        Integer idProd = inserir(
                "INSERT INTO produtos (nome, id_categoria, preco, ativo) VALUES (?, ?, 30.00, 1)",
                "Lanche " + sufixo, idCat);
        jdbc.update("INSERT INTO produto_ingredientes (id_produto, id_ingrediente, quantidade) VALUES (?, ?, 2.000)",
                idProd, idIng);

        Integer idCaixa = inserir(
                "INSERT INTO funcionarios (nome, cargo, perfil, login, senha_hash, ativo) "
                        + "VALUES (?, 'Caixa', 'CAIXA', ?, '$2a$10$hash', 1)",
                "Caixa " + sufixo, "cx." + sufixo);
        Integer idCozinha = inserir(
                "INSERT INTO funcionarios (nome, cargo, perfil, login, senha_hash, ativo) "
                        + "VALUES (?, 'Cozinha', 'COZINHA', ?, '$2a$10$hash', 1)",
                "Cozinha " + sufixo, "cz." + sufixo);

        UsuarioLogado caixa = usuario(idCaixa, "cx." + sufixo, Perfil.CAIXA);
        MockHttpSession sessao = new MockHttpSession();

        // 1. Adiciona item e confirma pedido via HTTP
        mvc.perform(post("/pdv/itens")
                        .session(sessao)
                        .with(user(caixa))
                        .with(csrf())
                        .param("idProduto", String.valueOf(idProd))
                        .param("quantidade", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pdv"))
                .andExpect(flash().attributeExists("ok"));

        Integer antes = jdbc.queryForObject("SELECT COALESCE(MAX(id_pedido), 0) FROM pedidos", Integer.class);

        mvc.perform(post("/pdv/confirmar")
                        .session(sessao)
                        .with(user(caixa))
                        .with(csrf())
                        .param("formaPagamento", "PIX")
                        .param("observacao", "Teste Web"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pdv"))
                .andExpect(flash().attributeExists("ok"));

        Integer idPedido1 = jdbc.queryForObject(
                "SELECT id_pedido FROM pedidos WHERE id_pedido > ? AND id_funcionario = ? ORDER BY id_pedido DESC LIMIT 1",
                Integer.class,
                antes,
                idCaixa);

        // Pedido em EM_PREPARO aparece no /pdv com modal de cancelamento; ainda nao pode entregar
        mvc.perform(get("/pdv").param("busca", "Lanche " + sufixo).with(user(caixa)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("4 un. disp.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Indisponível"))))
                .andExpect(content().string(containsString("#" + idPedido1)))
                .andExpect(content().string(containsString("data-confirmar-mensagem")));

        mvc.perform(post("/pdv/pedidos/{id}/entregar", idPedido1)
                        .with(user(caixa))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", PedidoService.MENSAGEM_SO_ENTREGA_PRONTO));

        // Cozinha marca como PRONTO e caixa entrega
        jdbc.update("CALL sp_alterar_status_pedido(?, 'PRONTO', NULL, ?)", idPedido1, idCozinha);
        mvc.perform(post("/pdv/pedidos/{id}/entregar", idPedido1)
                        .with(user(caixa))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("ok"));

        assertThat(jdbc.queryForObject("SELECT status FROM pedidos WHERE id_pedido = ?", String.class, idPedido1))
                .isEqualTo("ENTREGUE");

        // 2. Cria segundo pedido e cancela pelo PDV (devolve ingrediente ao saldo)
        BigDecimal saldoAntes = jdbc.queryForObject(
                "SELECT saldo_atual FROM ingredientes WHERE id_ingrediente = ?", BigDecimal.class, idIng);

        mvc.perform(post("/pdv/itens")
                        .session(sessao)
                        .with(user(caixa))
                        .with(csrf())
                        .param("idProduto", String.valueOf(idProd))
                        .param("quantidade", "1"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/pdv/confirmar")
                        .session(sessao)
                        .with(user(caixa))
                        .with(csrf())
                        .param("formaPagamento", "DINHEIRO"))
                .andExpect(status().is3xxRedirection());

        Integer idPedido2 = jdbc.queryForObject(
                "SELECT id_pedido FROM pedidos WHERE id_pedido > ? AND id_funcionario = ? ORDER BY id_pedido DESC LIMIT 1",
                Integer.class,
                idPedido1,
                idCaixa);

        mvc.perform(post("/pdv/pedidos/{id}/cancelar", idPedido2)
                        .with(user(caixa))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("ok"));

        assertThat(jdbc.queryForObject("SELECT status FROM pedidos WHERE id_pedido = ?", String.class, idPedido2))
                .isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("SELECT saldo_atual FROM ingredientes WHERE id_ingrediente = ?", BigDecimal.class, idIng))
                .isEqualByComparingTo(saldoAntes);
    }

    @Test
    void deveRenderizarModalDeClienteNoPdvEVincularClientePelaSecao14SemImportarModuloCliente() throws Exception {
        String sufixo = "Cli Modal " + System.nanoTime();
        UsuarioLogado caixa = usuario(2, "ana.caixa", Perfil.CAIXA);
        MockHttpSession sessao = new MockHttpSession();

        mvc.perform(get("/pdv").session(sessao).with(user(caixa)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("modal-cliente-pdv")))
                .andExpect(content().string(containsString("/clientes/busca")))
                .andExpect(content().string(containsString("/clientes/rapido")))
                .andExpect(content().string(containsString("Venda de balcão (anônimo)")));

        // Erro de validacao no contrato da secao 14 retorna JSON com "erro" e "campos"
        mvc.perform(post("/clientes/rapido")
                        .with(user(caixa))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + sufixo + "\",\"telefone\":\"123\",\"cpf\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").isString())
                .andExpect(jsonPath("$.campos.telefone").isString());

        // Cadastro rapido valido cria cliente e /pdv/cliente vincula ao carrinho da sessao
        String corpoCriado = mvc.perform(post("/clientes/rapido")
                        .with(user(caixa))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + sufixo + "\",\"telefone\":\"48999887766\",\"cpf\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value(sufixo))
                .andExpect(jsonPath("$.telefone").value("(48) 99988-7766"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer idCliente = Integer.valueOf(corpoCriado.replaceAll(".*\"id\":(\\d+).*", "$1"));
        try {
            mvc.perform(get("/clientes/busca")
                            .param("q", sufixo)
                            .with(user(caixa)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(idCliente))
                    .andExpect(jsonPath("$[0].nome").value(sufixo));

            mvc.perform(post("/pdv/cliente")
                            .session(sessao)
                            .with(user(caixa))
                            .with(csrf())
                            .param("idCliente", String.valueOf(idCliente)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/pdv"))
                    .andExpect(flash().attributeExists("ok"));

            mvc.perform(get("/pdv").session(sessao).with(user(caixa)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(sufixo)));
        } finally {
            jdbc.update("DELETE FROM clientes WHERE id_cliente = ?", idCliente);
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
