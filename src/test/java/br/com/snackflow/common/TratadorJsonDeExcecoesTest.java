package br.com.snackflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindException;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exercita os dois tratadores com requisições reais (MockMvc autônomo, sem banco e sem Docker):
 * endpoints {@code @RestController} recebem JSON com status HTTP; telas {@code @Controller}
 * continuam sendo redirecionadas com toast.
 */
class TratadorJsonDeExcecoesTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    /** Endpoint JSON de exemplo, só de teste. Rotas sob /teste/api para não colidir com módulos. */
    @RestController
    @RequestMapping("/teste/api")
    static class ApiDeExemplo {

        public record ClienteRapido(@NotBlank(message = "Informe o nome.") String nome,
                             @Size(max = 5, message = "Telefone muito longo.") String telefone,
                             String cpf) {
        }

        /** Imita o formato do contrato: 201 com o cliente criado (cpf nulo quando ausente). */
        @PostMapping("/ok")
        ResponseEntity<Map<String, Object>> ok(@Valid @RequestBody ClienteRapido corpo) {
            Map<String, Object> cliente = new LinkedHashMap<>();
            cliente.put("id", 7);
            cliente.put("nome", corpo.nome());
            cliente.put("cpf", corpo.cpf());
            cliente.put("telefone", corpo.telefone());
            return ResponseEntity.status(201).body(cliente);
        }

        /** Imita a busca do contrato: ausente ou curta responde 200 com lista vazia, nunca erro. */
        @GetMapping("/busca")
        List<Map<String, Object>> busca(@RequestParam(name = "q", required = false) String q) {
            if (q == null || q.trim().length() < 2) {
                return List.of();
            }
            return List.of(Map.of("id", 1, "nome", "Mariana Costa"));
        }

        /** Restrição em parâmetro avulso (validação de método do Spring). */
        @GetMapping("/parametro")
        String parametro(@RequestParam("codigo") @Size(max = 5, message = "Código muito longo.") String codigo) {
            return codigo;
        }

        /**
         * Validação programática: lança {@link BindException} pura (não a subclasse
         * {@code MethodArgumentNotValidException}), que só o handler {@code parametrosInvalidos} trata.
         * O valor rejeitado fica no resultado, mas não pode ir à resposta.
         */
        @PostMapping("/bind")
        void bind() throws BindException {
            Map<String, Object> alvo = new LinkedHashMap<>();
            alvo.put("telefone", "valor-secreto-123");
            MapBindingResult resultado = new MapBindingResult(alvo, "cliente");
            resultado.rejectValue("telefone", "invalido", "Telefone inválido.");
            resultado.rejectValue("telefone", "outro", "Segunda mensagem do mesmo campo.");
            throw new BindException(resultado);
        }

        @PostMapping("/negocio")
        void negocio() {
            throw new RegraNegocioException("CPF inválido.");
        }

        @PostMapping("/negocio-sem-mensagem")
        void negocioSemMensagem() {
            throw new RegraNegocioException(null);
        }

        @PostMapping("/banco-regra")
        void bancoRegra() {
            throw new DataIntegrityViolationException("falha",
                    new SQLException("Estoque insuficiente para o pedido.", "45000"));
        }

        @PostMapping("/banco-tecnico")
        void bancoTecnico() {
            String bruta = "Duplicate entry '39053344705' for key 'clientes.uk_segredo'";
            throw new DataIntegrityViolationException(bruta, new SQLException(bruta, "23000"));
        }
    }

    /** Tela de exemplo, só de teste: deve manter o comportamento de redirecionar. */
    @Controller
    @RequestMapping("/teste/pagina")
    static class PaginaDeExemplo {

        @PostMapping("/falhar")
        String falhar() {
            throw new RegraNegocioException("Falha de página.");
        }
    }

    private MockMvc mvc;
    private Logger loggerDoTratador;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void configurar() {
        loggerDoTratador = (Logger) LoggerFactory.getLogger(TratadorJsonDeExcecoes.class);
        logs = new ListAppender<>();
        logs.start();
        loggerDoTratador.addAppender(logs);
        mvc = MockMvcBuilders.standaloneSetup(new ApiDeExemplo(), new PaginaDeExemplo())
                .setControllerAdvice(new TratadorJsonDeExcecoes(), new TratadorGlobalDeExcecoes())
                .build();
    }

    @AfterEach
    void removerCapturaDeLog() {
        loggerDoTratador.detachAppender(logs);
    }

    @Test
    void deveResponder201ComOClienteSemPassarPeloTratador() throws Exception {
        mvc.perform(post("/teste/api/ok").contentType(JSON)
                        .content("{\"nome\":\"Ana\",\"telefone\":\"123\",\"cpf\":\"39053344705\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nome").value("Ana"))
                .andExpect(jsonPath("$.cpf").value("39053344705"))
                .andExpect(jsonPath("$.erro").doesNotExist());
    }

    @Test
    void deveAceitarCadastroSemCpfDevolvendoCpfNulo() throws Exception {
        mvc.perform(post("/teste/api/ok").contentType(JSON).content("{\"nome\":\"Ana\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpf").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void deveResponderListaVaziaEmBuscaAusenteOuCurtaSemErro() throws Exception {
        mvc.perform(get("/teste/api/busca")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/teste/api/busca").param("q", " a ")).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/teste/api/busca").param("q", "ma")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Mariana Costa"));
    }

    @Test
    void deveResponder400ComONomeDoParametroEmRestricaoDeParametro() throws Exception {
        String corpo = mvc.perform(get("/teste/api/parametro").param("codigo", "abcdefg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(TratadorJsonDeExcecoes.MENSAGEM_DADOS_INVALIDOS))
                .andExpect(jsonPath("$.campos.codigo").value("Código muito longo."))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("abcdefg");
    }

    @Test
    void deveResponder400ComCamposEmBindExceptionSemRepetirOValorRejeitado() throws Exception {
        String corpo = mvc.perform(post("/teste/api/bind"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(TratadorJsonDeExcecoes.MENSAGEM_DADOS_INVALIDOS))
                .andExpect(jsonPath("$.campos.telefone").value("Telefone inválido."))
                .andReturn().getResponse().getContentAsString();

        // Uma mensagem por campo (a primeira) e o valor rejeitado nunca vai na resposta.
        assertThat(corpo).doesNotContain("valor-secreto-123").doesNotContain("Segunda mensagem");
    }

    @Test
    void deveResponder422ComMensagemEmRegraDeNegocio() throws Exception {
        mvc.perform(post("/teste/api/negocio"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.erro").value("CPF inválido."));
    }

    @Test
    void deveUsarMensagemGenericaQuandoARegraNaoTemMensagem() throws Exception {
        mvc.perform(post("/teste/api/negocio-sem-mensagem"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.erro").value(ErrosDoBanco.MENSAGEM_GENERICA));
    }

    @Test
    void deveResponder400ComOsCamposInvalidosEmErroDeValidacao() throws Exception {
        mvc.perform(post("/teste/api/ok").contentType(JSON).content("{\"nome\":\"\",\"telefone\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(TratadorJsonDeExcecoes.MENSAGEM_DADOS_INVALIDOS))
                .andExpect(jsonPath("$.campos.nome").value("Informe o nome."))
                .andExpect(jsonPath("$.campos.telefone").value("Telefone muito longo."));
    }

    @Test
    void naoDeveRepetirOValorRejeitadoNaResposta() throws Exception {
        String corpo = mvc.perform(post("/teste/api/ok").contentType(JSON)
                        .content("{\"nome\":\"Ana\",\"telefone\":\"valor-secreto-123\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("valor-secreto-123");
    }

    @Test
    void deveResponder400SemDetalhesEmJsonMalformado() throws Exception {
        String corpo = mvc.perform(post("/teste/api/ok").contentType(JSON).content("{nome: oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(TratadorJsonDeExcecoes.MENSAGEM_CORPO_INVALIDO))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("oops").doesNotContain("Exception").doesNotContain("ClienteRapido");
    }

    @Test
    void deveResponder422ComMensagemDoTriggerEmErroDeBanco() throws Exception {
        mvc.perform(post("/teste/api/banco-regra"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.erro").value("Estoque insuficiente para o pedido."));
    }

    @Test
    void deveResponder500GenericoSemExporDetalhesTecnicosDoBanco() throws Exception {
        String corpo = mvc.perform(post("/teste/api/banco-tecnico"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value(ErrosDoBanco.MENSAGEM_GENERICA))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("uk_segredo").doesNotContain("Duplicate").doesNotContain("39053344705");
    }

    @Test
    void deveRegistrarSoContextoSeguroNoLogDoErroTecnicoDeBanco() throws Exception {
        mvc.perform(post("/teste/api/banco-tecnico")).andExpect(status().isInternalServerError());

        assertThat(logs.list).hasSize(1);
        ILoggingEvent evento = logs.list.get(0);
        assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
        assertThat(evento.getThrowableProxy()).isNull();
        assertThat(evento.getFormattedMessage())
                .contains("/teste/api/banco-tecnico")
                .contains("23000")
                .doesNotContain("39053344705")
                .doesNotContain("uk_segredo")
                .doesNotContain("Duplicate");
    }

    @Test
    void naoDeveRegistrarAMensagemDoBancoNoLogDaRegraDeNegocio() throws Exception {
        mvc.perform(post("/teste/api/banco-regra")).andExpect(status().is(422));

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list.get(0).getThrowableProxy()).isNull();
        assertThat(logs.list.get(0).getFormattedMessage())
                .contains("/teste/api/banco-regra")
                .doesNotContain("Estoque insuficiente");
    }

    @Test
    void deveManterORedirecionamentoComToastNasTelasComReferer() throws Exception {
        mvc.perform(post("/teste/pagina/falhar").header("Referer", "http://localhost/teste/pagina?x=1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teste/pagina"))
                .andExpect(flash().attribute("erro", "Falha de página."));
    }

    @Test
    void deveRedirecionarTelaParaARaizSemReferer() throws Exception {
        mvc.perform(post("/teste/pagina/falhar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("erro", "Falha de página."));
    }

    @Test
    void deveDescartarOHostDoRefererERedirecionarSoParaOCaminho() throws Exception {
        mvc.perform(post("/teste/pagina/falhar").header("Referer", "http://outro-site.example/teste/pagina"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teste/pagina"));
    }
}
