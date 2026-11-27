package br.com.snackflow.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.snackflow.TestcontainersConfiguration;
import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * API de clientes com o MySQL real (trigger, índice único, collation). Só cria dados com o prefixo
 * único do teste e os remove ao final; não depende dos dados de exemplo nem conta registros globais.
 * Não chama a procedure, mas também não é {@code @Transactional}: o MockMvc grava em transação própria.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClienteApiIntegracaoTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private String prefixo;
    private final UsuarioLogado caixa = new UsuarioLogado(2, "Ana", "ana.caixa", "hash", Perfil.CAIXA, true);

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        prefixo = "it-cli-" + System.nanoTime();
        limpar();
    }

    @AfterEach
    void limpar() {
        if (prefixo != null) {
            jdbc.update("DELETE FROM clientes WHERE nome LIKE ?", prefixo + "%");
        }
    }

    private String json(String nome, String telefone, String cpf) {
        return "{\"nome\":" + (nome == null ? "null" : "\"" + nome + "\"")
                + ",\"telefone\":" + (telefone == null ? "null" : "\"" + telefone + "\"")
                + ",\"cpf\":" + (cpf == null ? "null" : "\"" + cpf + "\"") + "}";
    }

    private org.springframework.test.web.servlet.ResultActions cadastrar(String corpo) throws Exception {
        return mvc.perform(post("/clientes/rapido").with(user(caixa)).with(csrf()).contentType(JSON).content(corpo));
    }

    private void inserir(String nome, boolean ativo) {
        jdbc.update("INSERT INTO clientes (nome, telefone, ativo) VALUES (?, NULL, ?)", nome, ativo ? 1 : 0);
    }

    // ---------- acesso ----------

    @ParameterizedTest
    @ValueSource(strings = {"ESTOQUE", "COZINHA"})
    void deveNegarAcessoAosPerfisSemPermissao(String perfil) throws Exception {
        mvc.perform(get("/clientes/busca").param("q", "ana").with(user("u").roles(perfil)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/rapido").with(user("u").roles(perfil)).with(csrf())
                        .contentType(JSON).content(json("Ana", "48987654321", null)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAIXA", "ADMIN"})
    void devePermitirBuscaParaCaixaEAdmin(String perfil) throws Exception {
        mvc.perform(get("/clientes/busca").param("q", prefixo).with(user("u").roles(perfil)))
                .andExpect(status().isOk());
    }

    @Test
    void deveRedirecionarAnonimoParaLogin() throws Exception {
        mvc.perform(get("/clientes/busca").param("q", "ana")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void deveExigirCsrfNoCadastro() throws Exception {
        mvc.perform(post("/clientes/rapido").with(user(caixa)).contentType(JSON)
                        .content(json(prefixo + "-sem-csrf", "48987654321", null)))
                .andExpect(status().isForbidden());
    }

    // ---------- busca ----------

    @Test
    void deveResponderListaVaziaEmBuscaAusenteOuCurta() throws Exception {
        mvc.perform(get("/clientes/busca").with(user(caixa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/clientes/busca").param("q", " a ").with(user(caixa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/clientes/busca").param("q", "7").with(user(caixa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deveBuscarSoAtivosSemDiferenciarMaiusculasEOrdenadosELimitadosA10() throws Exception {
        for (int i = 12; i >= 1; i--) {
            inserir(prefixo + "-" + String.format("%02d", i), true);
        }
        inserir(prefixo + "-00-inativo", false);

        mvc.perform(get("/clientes/busca").param("q", prefixo.toUpperCase()).with(user(caixa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].nome").value(prefixo + "-01"))
                .andExpect(jsonPath("$[9].nome").value(prefixo + "-10"));
    }

    @Test
    void deveTratarPercentualComoTextoLiteralNaBusca() throws Exception {
        inserir(prefixo + "axb", true);

        mvc.perform(get("/clientes/busca").param("q", prefixo + "a%b").with(user(caixa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/clientes/busca").param("q", prefixo + "a_b").with(user(caixa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/clientes/busca").param("q", prefixo + "axb").with(user(caixa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- cadastro rápido ----------

    @Test
    void deveCadastrarSemCpfEEncontrarNaBusca() throws Exception {
        cadastrar(json("  " + prefixo + "   Silva ", "(48) 98765-4321", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value(prefixo + " Silva"))
                .andExpect(jsonPath("$.cpf").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.telefone").value("(48) 98765-4321"));

        mvc.perform(get("/clientes/busca").param("q", prefixo).with(user(caixa)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value(prefixo + " Silva"));
    }

    @Test
    void deveGravarCpfSoComDigitosEBuscarPorCpfComPontuacao() throws Exception {
        String cpf = cpfValido(String.format("%09d", System.nanoTime() % 1_000_000_000L));
        String formatado = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-"
                + cpf.substring(9);

        cadastrar(json(prefixo + " Com CPF", "4898765432", formatado))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.telefone").value("(48) 9876-5432"));

        Integer gravados = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE cpf = ?", Integer.class, cpf);
        assertThat(gravados).isEqualTo(1);
        mvc.perform(get("/clientes/busca").param("q", formatado).with(user(caixa)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cpf").value(cpf));
    }

    @Test
    void deveResponder422EmCpfDuplicadoSemRepetirOCpf() throws Exception {
        String cpf = cpfValido(String.format("%09d", System.nanoTime() % 1_000_000_000L));
        cadastrar(json(prefixo + " A", "48987654321", cpf)).andExpect(status().isCreated());

        String corpo = cadastrar(json(prefixo + " B", "48987654321", cpf))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.erro").value(ClienteService.MENSAGEM_CPF_DUPLICADO))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain(cpf);
        Integer gravados = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE cpf = ?", Integer.class, cpf);
        assertThat(gravados).isEqualTo(1);
    }

    @Test
    void deveResponder422ComTextoDaInterfaceEmCpfInvalido() throws Exception {
        cadastrar(json(prefixo + " X", "48987654321", "111.111.111-11"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.erro").value("CPF inválido."));
        cadastrar(json(prefixo + " Y", "48987654321", "39053344706"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.erro").value("CPF inválido."));
    }

    @Test
    void deveResponder400ComCamposEmNomeOuTelefoneAusenteOuMalformado() throws Exception {
        cadastrar(json("   ", "48987654321", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Dados inválidos."))
                .andExpect(jsonPath("$.campos.nome").value("Informe o nome."));
        cadastrar(json(prefixo + " T", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefone").value("Informe o telefone."));
        cadastrar(json(prefixo + " T", "123", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefone").value("Telefone inválido."));
    }

    @Test
    void deveResponder400EmJsonMalformado() throws Exception {
        cadastrar("{nome: oops")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Corpo da requisição inválido."));
    }

    /** Completa 9 dígitos base com os 2 verificadores (mesma regra de fn_cpf_valido). */
    private static String cpfValido(String base9) {
        String base = base9.chars().distinct().count() == 1 ? "123456789" : base9;
        int dv1 = digito(base, 10);
        int dv2 = digito(base + dv1, 11);
        return base + dv1 + dv2;
    }

    private static int digito(String digitos, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < digitos.length(); i++) {
            soma += (digitos.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
