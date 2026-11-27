package br.com.snackflow.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Edição e inativação com o MySQL real. Dados próprios com prefixo único, removidos ao final. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClienteEdicaoIntegracaoTest {

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
        prefixo = "it-edit-" + System.nanoTime();
        limpar();
    }

    @AfterEach
    void limpar() {
        if (prefixo != null) {
            jdbc.update("DELETE FROM clientes WHERE nome LIKE ?", prefixo + "%");
        }
    }

    private int criar(String sufixo, String cpf) {
        String nome = prefixo + sufixo;
        jdbc.update("INSERT INTO clientes (nome, cpf, telefone) VALUES (?, ?, '(48) 98765-4321')", nome, cpf);
        return jdbc.queryForObject("SELECT id_cliente FROM clientes WHERE nome = ?", Integer.class, nome);
    }

    private String json(String nome, String telefone, String cpf) {
        return "{\"nome\":\"" + nome + "\",\"telefone\":\"" + telefone + "\",\"cpf\":"
                + (cpf == null ? "null" : "\"" + cpf + "\"") + "}";
    }

    private ResultActions editar(int id, String corpo) throws Exception {
        return mvc.perform(put("/clientes/" + id).with(user(caixa)).with(csrf()).contentType(JSON).content(corpo));
    }

    @Test
    void deveEditarEPersistir() throws Exception {
        int id = criar("-a", null);
        String cpf = cpfValido(String.format("%09d", System.nanoTime() % 1_000_000_000L));

        editar(id, json(prefixo + "-editado", "4898765432", cpf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nome").value(prefixo + "-editado"))
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.telefone").value("(48) 9876-5432"));

        String nomeGravado = jdbc.queryForObject("SELECT nome FROM clientes WHERE id_cliente = ?", String.class, id);
        assertThat(nomeGravado).isEqualTo(prefixo + "-editado");
    }

    @Test
    void deveManterOProprioCpfNaEdicao() throws Exception {
        String cpf = cpfValido(String.format("%09d", System.nanoTime() % 1_000_000_000L));
        int id = criar("-a", cpf);

        editar(id, json(prefixo + "-a", "48987654321", cpf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(cpf));
    }

    @Test
    void deveRecusarCpfDeOutroClienteSemRepetirOCpf() throws Exception {
        String cpf = cpfValido(String.format("%09d", System.nanoTime() % 1_000_000_000L));
        criar("-dono", cpf);
        int outro = criar("-outro", null);

        String corpo = editar(outro, json(prefixo + "-outro", "48987654321", cpf))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.erro").value(ClienteService.MENSAGEM_CPF_DUPLICADO))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain(cpf);
    }

    @Test
    void deveResponder422EmCpfInvalidoOuClienteInexistente() throws Exception {
        int id = criar("-a", null);

        editar(id, json(prefixo + "-a", "48987654321", "39053344706"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.erro").value("CPF inválido."));
        editar(Integer.MAX_VALUE, json(prefixo + "-a", "48987654321", null))
                .andExpect(status().is(422)).andExpect(jsonPath("$.erro").value("Cliente não encontrado."));
    }

    @Test
    void deveResponder400EmCampoInvalido() throws Exception {
        int id = criar("-a", null);

        editar(id, json(" ", "123", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Dados inválidos."))
                .andExpect(jsonPath("$.campos.nome").value("Informe o nome."))
                .andExpect(jsonPath("$.campos.telefone").value("Telefone inválido."));
    }

    @Test
    void deveInativarSemExcluirEEscondeDaBusca() throws Exception {
        int id = criar("-a", null);

        mvc.perform(post("/clientes/" + id + "/inativar").with(user(caixa)).with(csrf()))
                .andExpect(status().isNoContent());

        Integer ativo = jdbc.queryForObject("SELECT ativo FROM clientes WHERE id_cliente = ?", Integer.class, id);
        assertThat(ativo).isZero();
        mvc.perform(get("/clientes/busca").param("q", prefixo).with(user(caixa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deveRecusarInativarClienteInexistente() throws Exception {
        mvc.perform(post("/clientes/" + Integer.MAX_VALUE + "/inativar").with(user(caixa)).with(csrf()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.erro").value("Cliente não encontrado."));
    }

    @Test
    void deveExigirCsrfNaEdicaoENaInativacao() throws Exception {
        int id = criar("-a", null);

        mvc.perform(put("/clientes/" + id).with(user(caixa)).contentType(JSON)
                        .content(json(prefixo + "-a", "48987654321", null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/" + id + "/inativar").with(user(caixa)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ESTOQUE", "COZINHA"})
    void deveNegarEdicaoEInativacaoAosPerfisSemPermissao(String perfil) throws Exception {
        int id = criar("-a", null);

        mvc.perform(put("/clientes/" + id).with(user("u").roles(perfil)).with(csrf()).contentType(JSON)
                        .content(json(prefixo + "-a", "48987654321", null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/" + id + "/inativar").with(user("u").roles(perfil)).with(csrf()))
                .andExpect(status().isForbidden());
    }

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
