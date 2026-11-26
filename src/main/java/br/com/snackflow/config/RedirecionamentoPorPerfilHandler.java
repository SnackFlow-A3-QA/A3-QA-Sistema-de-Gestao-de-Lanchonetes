package br.com.snackflow.config;

import br.com.snackflow.funcionario.UsuarioLogado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Após o login: ADMIN e CAIXA vão para /pdv, COZINHA para /cozinha, ESTOQUE para /estoque. */
@Component
class RedirecionamentoPorPerfilHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest requisicao, HttpServletResponse resposta,
                                        Authentication autenticacao) throws IOException {
        String destino = autenticacao.getPrincipal() instanceof UsuarioLogado usuario
                ? usuario.getPerfil().getPaginaInicial()
                : "/";
        resposta.sendRedirect(requisicao.getContextPath() + destino);
    }
}
