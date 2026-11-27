package br.com.snackflow.funcionario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.snackflow.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Telas de funcionários com o MySQL real: rotas, perfis, CSRF, validação, login único, BCrypt e
 * autenticação ponta a ponta. Só cria funcionários com o prefixo único do teste e os remove ao final
 * (não há pedidos nem movimentações ligados a eles). Não chama a procedure.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FuncionarioWebIntegracaoTest {

    private static final String SENHA = "segredo1";

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder codificador;

    private MockMvc mvc;
    private String prefixo;
    private final UsuarioLogado admin = new UsuarioLogado(1, "Carlos Silva", "admin", "hash", Perfil.ADMIN, true);

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        prefixo = "it-func-" + System.nanoTime();
        limpar();
    }

    @AfterEach
    void limpar() {
        if (prefixo != null) {
            jdbc.update("DELETE FROM funcionarios WHERE login LIKE ?", prefixo + "%");
        }
    }

    private int criarNoBanco(String sufixo, Perfil perfil, boolean ativo) {
        String login = prefixo + sufixo;
        jdbc.update("INSERT INTO funcionarios (nome, cargo, perfil, login, senha_hash, ativo) VALUES (?, ?, ?, ?, ?, ?)",
                "Nome " + login, "Cargo", perfil.name(), login, codificador.encode(SENHA), ativo ? 1 : 0);
        return idDe(login);
    }

    private int idDe(String login) {
        return jdbc.queryForObject("SELECT id_funcionario FROM funcionarios WHERE login = ?", Integer.class, login);
    }

    private ResultActions cadastrar(String nome, String perfil, String login, String senha) throws Exception {
        return mvc.perform(post("/funcionarios/novo").with(user(admin)).with(csrf())
                .param("nome", nome).param("cargo", "Atendente").param("perfil", perfil)
                .param("login", login).param("senha", senha));
    }

    private MockHttpServletRequestBuilder edicao(int id, String login) {
        return post("/funcionarios/" + id + "/editar").with(user(admin)).with(csrf())
                .param("nome", "Editado").param("cargo", "Gerente").param("perfil", "ADMIN").param("login", login);
    }

    // ---------- acesso ----------

    @ParameterizedTest
    @ValueSource(strings = {"CAIXA", "COZINHA", "ESTOQUE"})
    void deveNegarAcessoAosPerfisSemPermissao(String perfil) throws Exception {
        mvc.perform(get("/funcionarios").with(user("u").roles(perfil))).andExpect(status().isForbidden());
        mvc.perform(get("/funcionarios/novo").with(user("u").roles(perfil))).andExpect(status().isForbidden());
        mvc.perform(post("/funcionarios/novo").with(user("u").roles(perfil)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/funcionarios/1/inativar").with(user("u").roles(perfil)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRedirecionarAnonimoParaLoginEExigirCsrf() throws Exception {
        mvc.perform(get("/funcionarios")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/funcionarios/novo").with(user(admin))
                        .param("nome", "X").param("cargo", "Y").param("perfil", "CAIXA")
                        .param("login", prefixo + "-csrf").param("senha", SENHA))
                .andExpect(status().isForbidden());
    }

    // ---------- listagem e telas ----------

    @Test
    void deveListarComOsDadosSemExporOHash() throws Exception {
        criarNoBanco("-a", Perfil.CAIXA, true);
        criarNoBanco("-b", Perfil.COZINHA, false);

        String html = mvc.perform(get("/funcionarios").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(prefixo + "-a", prefixo + "-b", "Novo funcionário", "Redefinir senha",
                "Reativar", "Inativo", "Ativo");
        assertThat(html).doesNotContain("$2a$");
    }

    @Test
    void deveExibirFormularioDeCadastroComSenhaEFormularioDeEdicaoSemSenha() throws Exception {
        int id = criarNoBanco("-a", Perfil.CAIXA, true);

        String cadastro = mvc.perform(get("/funcionarios/novo").with(user(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(cadastro).contains("Novo funcionário", "name=\"senha\"", "type=\"password\"", "Administrador");

        String edicao = mvc.perform(get("/funcionarios/" + id + "/editar").with(user(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(edicao).contains("Editar funcionário", "name=\"login\"", prefixo + "-a")
                .doesNotContain("name=\"senha\"")
                .containsPattern("value=\"CAIXA\"[^>]*selected");
    }

    @Test
    void deveRedirecionarComToastQuandoOFuncionarioDaEdicaoNaoExiste() throws Exception {
        mvc.perform(get("/funcionarios/" + Integer.MAX_VALUE + "/editar").with(user(admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", "Funcionário não encontrado."));
    }

    // ---------- cadastro ----------

    @Test
    void deveCadastrarComLoginEmMinusculasEHashBcrypt() throws Exception {
        cadastrar("Fernanda Lima", "CAIXA", (prefixo + "-Novo").toUpperCase(), SENHA)
                .andExpect(redirectedUrl("/funcionarios"))
                .andExpect(flash().attribute("ok", "Funcionário cadastrado."));

        String login = prefixo + "-novo";
        String gravado = jdbc.queryForObject("SELECT login FROM funcionarios WHERE login LIKE ?", String.class,
                prefixo + "%");
        String hash = jdbc.queryForObject("SELECT senha_hash FROM funcionarios WHERE login = ?", String.class, login);
        Integer ativo = jdbc.queryForObject("SELECT ativo FROM funcionarios WHERE login = ?", Integer.class, login);
        assertThat(gravado).isEqualTo(login);
        assertThat(hash).isNotEqualTo(SENHA).startsWith("$2");
        assertThat(codificador.matches(SENHA, hash)).isTrue();
        assertThat(ativo).isEqualTo(1);
    }

    @Test
    void deveRecusarLoginDuplicadoMesmoComCaixaDiferente() throws Exception {
        criarNoBanco("-dup", Perfil.CAIXA, true);

        String html = cadastrar("Outro", "CAIXA", (prefixo + "-DUP"), SENHA)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Já existe um funcionário com este login.");
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM funcionarios WHERE login LIKE ?", Integer.class,
                prefixo + "%");
        assertThat(total).isEqualTo(1);
    }

    @Test
    void deveMostrarErrosDeValidacaoNoFormulario() throws Exception {
        String html = mvc.perform(post("/funcionarios/novo").with(user(admin)).with(csrf())
                        .param("nome", " ").param("cargo", "Atendente").param("perfil", "CAIXA")
                        .param("login", "com espaco").param("senha", "12345"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Informe o nome.", "O login não pode ter espaços.",
                "A senha deve ter de 6 a 72 caracteres.", "sf-invalido");
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM funcionarios WHERE login = ?", Integer.class,
                "com espaco");
        assertThat(total).isZero();
    }

    // ---------- edição ----------

    @Test
    void deveEditarMantendoOProprioLoginEASenha() throws Exception {
        int id = criarNoBanco("-ed", Perfil.CAIXA, true);
        String hashAntes = jdbc.queryForObject("SELECT senha_hash FROM funcionarios WHERE id_funcionario = ?",
                String.class, id);

        mvc.perform(edicao(id, prefixo + "-ED"))
                .andExpect(redirectedUrl("/funcionarios"))
                .andExpect(flash().attribute("ok", "Funcionário atualizado."));

        String nome = jdbc.queryForObject("SELECT nome FROM funcionarios WHERE id_funcionario = ?", String.class, id);
        String perfil = jdbc.queryForObject("SELECT perfil FROM funcionarios WHERE id_funcionario = ?", String.class, id);
        String hashDepois = jdbc.queryForObject("SELECT senha_hash FROM funcionarios WHERE id_funcionario = ?",
                String.class, id);
        assertThat(nome).isEqualTo("Editado");
        assertThat(perfil).isEqualTo("ADMIN");
        assertThat(hashDepois).isEqualTo(hashAntes);
    }

    @Test
    void deveRecusarLoginDeOutroFuncionarioNaEdicao() throws Exception {
        criarNoBanco("-dono", Perfil.CAIXA, true);
        int outro = criarNoBanco("-outro", Perfil.CAIXA, true);

        String html = mvc.perform(edicao(outro, prefixo + "-dono"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Já existe um funcionário com este login.");
    }

    // ---------- senha ----------

    @Test
    void deveRedefinirSenhaEAutenticarComANova() throws Exception {
        int id = criarNoBanco("-sn", Perfil.CAIXA, true);

        mvc.perform(post("/funcionarios/" + id + "/senha").with(user(admin)).with(csrf()).param("senha", "outra-senha"))
                .andExpect(redirectedUrl("/funcionarios"))
                .andExpect(flash().attribute("ok", "Senha redefinida."));

        mvc.perform(post("/login").with(csrf()).param("login", prefixo + "-sn").param("senha", "outra-senha"))
                .andExpect(redirectedUrl("/pdv"));
        mvc.perform(post("/login").with(csrf()).param("login", prefixo + "-sn").param("senha", SENHA))
                .andExpect(redirectedUrl("/login?erro"));
    }

    @Test
    void deveRecusarSenhaCurtaNaRedefinicao() throws Exception {
        int id = criarNoBanco("-sc", Perfil.CAIXA, true);

        String html = mvc.perform(post("/funcionarios/" + id + "/senha").with(user(admin)).with(csrf())
                        .param("senha", "123"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("A senha deve ter de 6 a 72 caracteres.");
        String hash = jdbc.queryForObject("SELECT senha_hash FROM funcionarios WHERE id_funcionario = ?",
                String.class, id);
        assertThat(codificador.matches(SENHA, hash)).isTrue();
    }

    // ---------- situação ----------

    @Test
    void deveInativarImpedirLoginEReativar() throws Exception {
        int id = criarNoBanco("-in", Perfil.CAIXA, true);

        mvc.perform(post("/funcionarios/" + id + "/inativar").with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/funcionarios"))
                .andExpect(flash().attribute("ok", "Funcionário inativado."));
        assertThat(jdbc.queryForObject("SELECT ativo FROM funcionarios WHERE id_funcionario = ?", Integer.class, id))
                .isZero();
        mvc.perform(post("/login").with(csrf()).param("login", prefixo + "-in").param("senha", SENHA))
                .andExpect(redirectedUrl("/login?erro"));

        mvc.perform(post("/funcionarios/" + id + "/ativar").with(user(admin)).with(csrf()))
                .andExpect(flash().attribute("ok", "Funcionário reativado."));
        mvc.perform(post("/login").with(csrf()).param("login", prefixo + "-in").param("senha", SENHA))
                .andExpect(redirectedUrl("/pdv"));
    }

    @Test
    void deveRecusarInativarAPropriaConta() throws Exception {
        int id = criarNoBanco("-eu", Perfil.ADMIN, true);
        UsuarioLogado eu = new UsuarioLogado(id, "Eu", prefixo + "-eu", "hash", Perfil.ADMIN, true);

        mvc.perform(post("/funcionarios/" + id + "/inativar").with(user(eu)).with(csrf()))
                .andExpect(redirectedUrl("/funcionarios"))
                .andExpect(flash().attribute("erro", "Você não pode inativar a sua própria conta."));

        assertThat(jdbc.queryForObject("SELECT ativo FROM funcionarios WHERE id_funcionario = ?", Integer.class, id))
                .isEqualTo(1);
    }
}
