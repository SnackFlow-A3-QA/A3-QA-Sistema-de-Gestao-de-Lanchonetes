package br.com.snackflow.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Rede de segurança para exceções que o Controller não tratou.
 *
 * <p>O padrão do projeto é o Controller capturar {@link RegraNegocioException} e mostrar a
 * mensagem no próprio formulário. Se isso não acontecer, este tratador devolve o usuário à
 * tela anterior com a mensagem no toast (flash {@code erro}). Exceções de acesso (403) e de
 * rota inexistente (404) não passam por aqui: seguem para as páginas de erro do sistema.
 */
@ControllerAdvice
class TratadorGlobalDeExcecoes {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorGlobalDeExcecoes.class);
    private static final String REFERER = "Referer";
    private static final String REDIRECT_RAIZ = "redirect:/";

    @ExceptionHandler(RegraNegocioException.class)
    String regraDeNegocio(RegraNegocioException erro, HttpServletRequest requisicao, RedirectAttributes flash) {
        flash.addFlashAttribute("erro", erro.getMessage());
        return voltar(requisicao);
    }

    @ExceptionHandler(DataAccessException.class)
    String erroDeBanco(DataAccessException erro, HttpServletRequest requisicao, RedirectAttributes flash) {
        LOG.error("Falha de acesso a dados em {}", requisicao.getRequestURI(), erro);
        flash.addFlashAttribute("erro", ErrosDoBanco.extrairMensagem(erro));
        return voltar(requisicao);
    }

    /** Volta para o caminho (somente o caminho) da tela de origem; sem Referer válido, vai para a raiz. */
    private String voltar(HttpServletRequest requisicao) {
        String origem = requisicao.getHeader(REFERER);
        if (origem == null || origem.isBlank()) {
            return REDIRECT_RAIZ;
        }
        try {
            String caminho = URI.create(origem).getRawPath();
            String contexto = requisicao.getContextPath();
            if (caminho == null || !caminho.startsWith("/") || !pertenceAoContexto(caminho, contexto)) {
                return REDIRECT_RAIZ;
            }
            String relativo = caminho.substring(contexto.length());
            return "redirect:" + (relativo.isEmpty() ? "/" : relativo);
        } catch (IllegalArgumentException e) {
            return REDIRECT_RAIZ;
        }
    }

    private boolean pertenceAoContexto(String caminho, String contexto) {
        return contexto.isEmpty() || caminho.equals(contexto) || caminho.startsWith(contexto + "/");
    }
}
