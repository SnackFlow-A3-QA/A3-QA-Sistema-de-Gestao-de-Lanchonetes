package br.com.snackflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.snackflow.TestcontainersConfiguration;
import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Com o MySQL real (trigger e índice único de {@code clientes}), confirma o que o tratador JSON
 * devolve e registra em log diante de erros do banco.
 *
 * <ul>
 *   <li>Trigger de CPF inválido (SQLSTATE 45000): 422 com a mensagem do banco ({@code CPF invalido},
 *       sem acento; é o texto do trigger, não o texto de interface, que será {@code CPF inválido.}).
 *   <li>Índice único de CPF violado direto pelo {@code JdbcTemplate} (SQLSTATE 23000,
 *       {@link DuplicateKeyException}): 500 genérico, porque aqui é erro técnico bruto da persistência.
 * </ul>
 *
 * <p>Estes testes NÃO definem o contrato final da API de clientes. No contrato escolhido, o Service
 * futuro verifica a duplicidade antes de gravar, converte uma corrida de índice único
 * ({@link DuplicateKeyException}) em {@link RegraNegocioException} e a API responde 422. Cria só
 * dados próprios e os remove ao final.
 *
 * <p>O controller de exemplo é uma classe aninhada, que o Spring Boot não varre como componente;
 * por isso é importado explicitamente em {@code @Import}, e o {@code @BeforeEach} confirma que ele
 * está registrado como handler antes de qualquer requisição (sem isso, a resposta seria 404).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TratadorJsonBancoIntegracaoTest.InsercaoDeExemplo.class})
class TratadorJsonBancoIntegracaoTest {

    /** Só de teste, fora do espaço /clientes para não colidir com o módulo real. */
    @RestController
    @RequestMapping("/teste/api-banco")
    static class InsercaoDeExemplo {

        private final JdbcTemplate jdbc;

        InsercaoDeExemplo(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @PostMapping("/cliente")
        void inserir(@RequestParam("cpf") String cpf) {
            jdbc.update("INSERT INTO clientes (nome, cpf) VALUES (?, ?)", "teste-" + System.nanoTime(), cpf);
        }
    }

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private String cpfProprio;
    private ListAppender<ILoggingEvent> logs;
    private ch.qos.logback.classic.Logger loggerDoTratador;
    private final UsuarioLogado caixa = new UsuarioLogado(2, "Ana", "ana.caixa", "hash", Perfil.CAIXA, true);

    @BeforeEach
    void configurar() {
        assertThat(rotaDeTesteMapeada())
                .as("InsercaoDeExemplo deve estar registrado como handler (@Import na classe)")
                .isTrue();
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        cpfProprio = cpfValido(String.format("%09d", System.nanoTime() % 1_000_000_000L));
        limpar();

        loggerDoTratador = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TratadorJsonDeExcecoes.class);
        logs = new ListAppender<>();
        logs.start();
        loggerDoTratador.addAppender(logs);
    }

    @AfterEach
    void limpar() {
        if (loggerDoTratador != null && logs != null) {
            loggerDoTratador.detachAppender(logs);
        }
        if (cpfProprio != null) {
            jdbc.update("DELETE FROM clientes WHERE cpf = ?", cpfProprio);
        }
    }

    @Test
    void deveResponder422ComAMensagemDoTriggerParaCpfInvalido() throws Exception {
        String corpo = mvc.perform(post("/teste/api-banco/cliente").param("cpf", "11111111111")
                        .with(user(caixa)).with(csrf()))
                .andExpect(status().is(422))
                // Texto do trigger (sem acento); o Service futuro mostrará "CPF inválido.".
                .andExpect(jsonPath("$.erro").value("CPF invalido"))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("11111111111");
        assertThat(textoDosLogs()).doesNotContain("11111111111");
    }

    /**
     * Persistência direta: dois INSERTs com o mesmo CPF pelo {@code JdbcTemplate}. A segunda
     * tentativa é o erro bruto do índice único (23000), não uma regra de negócio da API.
     */
    @Test
    void deveResponder500GenericoNoErroBrutoDeIndiceUnicoDeCpfNaPersistenciaDireta() throws Exception {
        mvc.perform(post("/teste/api-banco/cliente").param("cpf", cpfProprio).with(user(caixa)).with(csrf()))
                .andExpect(status().isOk());
        assertThat(contarClientesComCpfProprio()).isEqualTo(1);

        String corpo = mvc.perform(post("/teste/api-banco/cliente").param("cpf", cpfProprio)
                        .with(user(caixa)).with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value(ErrosDoBanco.MENSAGEM_GENERICA))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo)
                .doesNotContain(cpfProprio)
                .doesNotContain("uk_clientes_cpf")
                .doesNotContain("Duplicate")
                .doesNotContain("23000")
                .doesNotContain("INSERT");
        assertThat(contarClientesComCpfProprio()).isEqualTo(1);

        // O erro técnico é registrado, mas só com contexto seguro.
        assertThat(logs.list).anyMatch(evento -> evento.getLevel() == Level.ERROR);
        assertThat(logs.list).allMatch(evento -> evento.getThrowableProxy() == null);
        // O nome da classe (DuplicateKeyException) é contexto seguro e aparece no log; o que não pode
        // aparecer é o texto do driver ("Duplicate entry '<cpf>' for key ...") nem o SQL.
        assertThat(textoDosLogs())
                .contains("/teste/api-banco/cliente")
                .contains("DuplicateKeyException")
                .contains("sqlstate=23000")
                .doesNotContain(cpfProprio)
                .doesNotContain("uk_clientes_cpf")
                .doesNotContain("Duplicate entry")
                .doesNotContain("INSERT");
    }

    /**
     * Persistência (não é o contrato do Service): a segunda inserção com o mesmo CPF lança
     * {@link DuplicateKeyException}, que é o gancho que o ClienteService futuro converterá em
     * {@link RegraNegocioException} (422) na corrida de índice único.
     */
    @Test
    void deveLancarDuplicateKeyExceptionNaPersistenciaDiretaParaCpfRepetido() {
        String sql = "INSERT INTO clientes (nome, cpf) VALUES (?, ?)";
        jdbc.update(sql, "teste-a-" + System.nanoTime(), cpfProprio);

        assertThatThrownBy(() -> jdbc.update(sql, "teste-b-" + System.nanoTime(), cpfProprio))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(contarClientesComCpfProprio()).isEqualTo(1);
    }

    private boolean rotaDeTesteMapeada() {
        return contexto.getBeansOfType(RequestMappingHandlerMapping.class).values().stream()
                .flatMap(mapeamento -> mapeamento.getHandlerMethods().values().stream())
                .anyMatch(metodo -> InsercaoDeExemplo.class.isAssignableFrom(metodo.getBeanType()));
    }

    private int contarClientesComCpfProprio() {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE cpf = ?", Integer.class, cpfProprio);
        return total == null ? 0 : total;
    }

    private String textoDosLogs() {
        List<ILoggingEvent> eventos = logs.list;
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent evento : eventos) {
            texto.append(evento.getFormattedMessage()).append('\n');
        }
        return texto.toString();
    }

    /** Completa 9 dígitos base com os 2 dígitos verificadores (mesma regra de fn_cpf_valido). */
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
