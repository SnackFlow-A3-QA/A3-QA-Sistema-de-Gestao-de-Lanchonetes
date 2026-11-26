package br.com.snackflow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.snackflow.TestcontainersConfiguration;
import br.com.snackflow.funcionario.Funcionario;
import br.com.snackflow.funcionario.FuncionarioRepository;
import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integração com o MySQL real (Testcontainers): login com BCrypt, redirecionamento
 * por perfil, 403 por rota e renderização do layout. Cada teste cria os próprios
 * funcionários e é desfeito ao final (@Transactional); não usa a procedure.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SegurancaELayoutIntegracaoTest {

    private static final String SEGREDO = "valor-de-teste-123";
    private static final String LOGIN_ERRO = "/login?erro";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private FuncionarioRepository repositorio;

    @Autowired
    private PasswordEncoder codificador;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    private Funcionario criarFuncionario(Perfil perfil, boolean ativo) {
        Funcionario funcionario = new Funcionario("Teste " + perfil, "Teste", perfil,
                "teste." + System.nanoTime(), codificador.encode(SEGREDO));
        funcionario.setAtivo(ativo);
        return repositorio.saveAndFlush(funcionario);
    }

    @Test
    void deveExibirPaginaDeLogin() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Entrar no sistema")));
    }

    @Test
    void deveExibirMensagemDeErroNoLogin() throws Exception {
        mvc.perform(get("/login").param("erro", ""))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Usuário inativo ou credenciais inválidas.")));
    }

    @Test
    void deveRedirecionarAnonimoParaLogin() throws Exception {
        mvc.perform(get("/pdv")).andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,/pdv", "CAIXA,/pdv", "COZINHA,/cozinha", "ESTOQUE,/estoque"})
    void deveAutenticarComBcryptEDirecionarPorPerfil(Perfil perfil, String destino) throws Exception {
        Funcionario funcionario = criarFuncionario(perfil, true);

        mvc.perform(post("/login").with(csrf())
                        .param("login", funcionario.getLogin()).param("senha", SEGREDO))
                .andExpect(redirectedUrl(destino));
    }

    @Test
    void deveRecusarSenhaErrada() throws Exception {
        Funcionario funcionario = criarFuncionario(Perfil.CAIXA, true);

        mvc.perform(post("/login").with(csrf())
                        .param("login", funcionario.getLogin()).param("senha", "outra"))
                .andExpect(redirectedUrl(LOGIN_ERRO));
    }

    @Test
    void deveRecusarFuncionarioInativo() throws Exception {
        Funcionario funcionario = criarFuncionario(Perfil.CAIXA, false);

        mvc.perform(post("/login").with(csrf())
                        .param("login", funcionario.getLogin()).param("senha", SEGREDO))
                .andExpect(redirectedUrl(LOGIN_ERRO));
    }

    @Test
    void deveRecusarLoginSemTokenCsrf() throws Exception {
        mvc.perform(post("/login").param("login", "x").param("senha", "y"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveEncerrarSessaoPorPost() throws Exception {
        UsuarioLogado usuario = new UsuarioLogado(1, "Teste", "teste", "hash", Perfil.CAIXA, true);

        mvc.perform(post("/logout").with(csrf()).with(user(usuario)))
                .andExpect(redirectedUrl("/login?saiu"));
    }

    @ParameterizedTest
    @CsvSource({
            "CAIXA,/produtos,true", "CAIXA,/funcionarios,true", "CAIXA,/relatorios,true",
            "CAIXA,/cozinha,true", "CAIXA,/estoque,true", "CAIXA,/pdv,false", "CAIXA,/clientes,false",
            "COZINHA,/pdv,true", "COZINHA,/estoque,true", "COZINHA,/cozinha,false",
            "ESTOQUE,/pdv,true", "ESTOQUE,/produtos,true", "ESTOQUE,/estoque,false",
            "ADMIN,/pdv,false", "ADMIN,/cozinha,false", "ADMIN,/estoque,false",
            "ADMIN,/produtos,false", "ADMIN,/funcionarios,false", "ADMIN,/relatorios,false"})
    void deveAplicarAcessoPorPerfil(String perfil, String rota, boolean proibido) throws Exception {
        int statusHttp = mvc.perform(get(rota).with(user("usuario").roles(perfil)))
                .andReturn().getResponse().getStatus();

        if (proibido) {
            assertThat(statusHttp).isEqualTo(403);
        } else {
            assertThat(statusHttp).isNotEqualTo(403);
        }
    }

    @Test
    void deveRenderizarLayoutComMenuDoPerfilAdmin() throws Exception {
        UsuarioLogado admin = new UsuarioLogado(1, "Carlos Silva", "admin", "hash", Perfil.ADMIN, true);

        String html = mvc.perform(get("/teste/layout").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("Carlos Silva", "Ponto de venda", "Cozinha (KDS)", "Estoque")
                .contains("Cardápio e produtos", "Funcionários", "Relatórios")
                .contains("Nenhum registro encontrado", "Em preparo", "id=\"modal-confirmacao\"")
                .contains("data-mensagem=\"Registro salvo\"", "Falha de exemplo");
    }

    @Test
    void deveOcultarAdministracaoNoMenuDoCaixa() throws Exception {
        UsuarioLogado caixa = new UsuarioLogado(2, "Ana Paula", "ana", "hash", Perfil.CAIXA, true);

        String html = mvc.perform(get("/teste/layout").with(user(caixa)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Ponto de venda").doesNotContain("Funcionários", "Relatórios", "ADMINISTRAÇÃO");
    }
}
