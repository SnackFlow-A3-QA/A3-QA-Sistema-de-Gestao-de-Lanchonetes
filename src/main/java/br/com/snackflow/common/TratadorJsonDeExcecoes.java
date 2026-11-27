package br.com.snackflow.common;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Tratador de exceções dos endpoints JSON (classes anotadas com {@link RestController}, usados
 * por chamadas {@code fetch}). Responde com status HTTP e corpo JSON em vez de redirecionar.
 *
 * <p>Vale só para {@code @RestController}: as telas ({@code @Controller}) continuam com o
 * {@link TratadorGlobalDeExcecoes}, que redireciona com toast. Um método {@code @Controller} com
 * {@code @ResponseBody} NÃO passa por aqui; use {@code @RestController} nos endpoints JSON.
 *
 * <p>Formato do corpo (sempre com a chave {@code erro}, texto seguro em português):
 * <pre>
 * 422 {"erro": "CPF inválido."}                                  regra de negócio / trigger do banco
 * 400 {"erro": "Dados inválidos.", "campos": {"nome": "..."}}     Bean Validation do corpo
 * 400 {"erro": "Dados inválidos.", "campos": {"q": "..."}}        restrição em @RequestParam/@PathVariable
 * 400 {"erro": "Corpo da requisição inválido."}                   JSON malformado ou de tipo errado
 * 500 {"erro": "Não foi possível concluir a operação. ..."}       falha técnica do banco, sem detalhes
 * </pre>
 *
 * <p>A chave {@code erro} está presente em todas as respostas produzidas por este tratador. Não há
 * captura genérica de {@link Exception}: acesso negado, rota inexistente, método ou tipo de mídia
 * não suportado e erros inesperados (403, 404, 405, 415, 500 não tratado) seguem o caminho padrão
 * do Spring, que não garante essa chave.
 *
 * <p>A mensagem de uma {@link RegraNegocioException} vai direto ao cliente: os Services devem
 * produzir mensagens seguras, sem SQL, nomes de índice nem dados pessoais (CPF, telefone etc.).
 *
 * <p>Log: erros do banco registram só contexto seguro (método e caminho da requisição, classe da
 * exceção, classe da causa-raiz e SQLSTATE). A mensagem do driver e o stack trace não são
 * registrados, porque podem conter SQL e valores como o CPF.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class TratadorJsonDeExcecoes {

    static final String MENSAGEM_DADOS_INVALIDOS = "Dados inválidos.";
    static final String MENSAGEM_CORPO_INVALIDO = "Corpo da requisição inválido.";
    static final String MENSAGEM_CAMPO_INVALIDO = "Valor inválido.";

    private static final Logger LOG = LoggerFactory.getLogger(TratadorJsonDeExcecoes.class);
    private static final int STATUS_REGRA_DE_NEGOCIO = 422;
    private static final int STATUS_REQUISICAO_INVALIDA = 400;
    private static final int STATUS_ERRO_INTERNO = 500;

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<Map<String, Object>> regraDeNegocio(RegraNegocioException erro) {
        String mensagem = erro.getMessage();
        if (mensagem == null || mensagem.isBlank()) {
            mensagem = ErrosDoBanco.MENSAGEM_GENERICA;
        }
        return resposta(STATUS_REGRA_DE_NEGOCIO, mensagem);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> erroDeBanco(DataAccessException erro, HttpServletRequest requisicao) {
        String mensagem = ErrosDoBanco.extrairMensagem(erro);
        if (ErrosDoBanco.MENSAGEM_GENERICA.equals(mensagem)) {
            LOG.error("Falha técnica de acesso a dados em {} {} ({})", requisicao.getMethod(),
                    requisicao.getRequestURI(), contextoSeguro(erro));
            return resposta(STATUS_ERRO_INTERNO, mensagem);
        }
        // Mensagem amigável de trigger/procedure (SQLSTATE 45000): é regra de negócio, não falha técnica.
        LOG.warn("Regra de negócio recusada pelo banco em {} {}", requisicao.getMethod(),
                requisicao.getRequestURI());
        return resposta(STATUS_REGRA_DE_NEGOCIO, mensagem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> corpoInvalido(MethodArgumentNotValidException erro) {
        return validacao(erro.getBindingResult());
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<Map<String, Object>> parametrosInvalidos(BindException erro) {
        return validacao(erro.getBindingResult());
    }

    /**
     * Restrições em parâmetros avulsos ({@code @RequestParam @Size ...}), validação de método do
     * Spring 6.1+. Mesmo formato do corpo; a chave é o nome do parâmetro (ou do campo, se o
     * argumento for um objeto validado).
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> parametroInvalido(HandlerMethodValidationException erro) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (ParameterValidationResult resultado : erro.getParameterValidationResults()) {
            if (resultado instanceof ParameterErrors erros) {
                for (FieldError campo : erros.getFieldErrors()) {
                    campos.putIfAbsent(campo.getField(), textoOuPadrao(campo.getDefaultMessage()));
                }
                continue;
            }
            String nome = resultado.getMethodParameter().getParameterName();
            for (MessageSourceResolvable motivo : resultado.getResolvableErrors()) {
                campos.putIfAbsent(nome == null ? "parametro" : nome, textoOuPadrao(motivo.getDefaultMessage()));
            }
        }
        return corpoDeValidacao(campos);
    }

    /** Não repete a mensagem do Jackson: ela pode citar classes e campos internos. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> corpoIlegivel(HttpMessageNotReadableException erro) {
        return resposta(STATUS_REQUISICAO_INVALIDA, MENSAGEM_CORPO_INVALIDO);
    }

    /** Uma mensagem por campo (a primeira); o valor rejeitado nunca vai na resposta. */
    private ResponseEntity<Map<String, Object>> validacao(BindingResult resultado) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError campo : resultado.getFieldErrors()) {
            campos.putIfAbsent(campo.getField(), textoOuPadrao(campo.getDefaultMessage()));
        }
        return corpoDeValidacao(campos);
    }

    private ResponseEntity<Map<String, Object>> corpoDeValidacao(Map<String, String> campos) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", MENSAGEM_DADOS_INVALIDOS);
        corpo.put("campos", campos);
        return ResponseEntity.status(STATUS_REQUISICAO_INVALIDA).body(corpo);
    }

    /** Só classes e SQLSTATE: nunca a mensagem do driver, que pode trazer SQL e o valor rejeitado. */
    private static String contextoSeguro(DataAccessException erro) {
        Throwable raiz = NestedExceptionUtils.getMostSpecificCause(erro);
        String sqlState = raiz instanceof SQLException sql ? sql.getSQLState() : null;
        return "exceção=" + erro.getClass().getSimpleName() + ", causa=" + raiz.getClass().getName()
                + ", sqlstate=" + sqlState;
    }

    private String textoOuPadrao(String mensagem) {
        return mensagem == null || mensagem.isBlank() ? MENSAGEM_CAMPO_INVALIDO : mensagem;
    }

    private ResponseEntity<Map<String, Object>> resposta(int status, String mensagem) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", mensagem);
        return ResponseEntity.status(status).body(corpo);
    }
}
