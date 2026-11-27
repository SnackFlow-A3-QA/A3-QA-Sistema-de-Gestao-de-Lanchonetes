package br.com.snackflow.config;

import br.com.snackflow.funcionario.UsuarioLogado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Exibe o formulário de login. A autenticação em si (POST /login) é do Spring Security. */
@Controller
class LoginController {

    @GetMapping("/login")
    String login(@AuthenticationPrincipal UsuarioLogado usuario) {
        return usuario == null ? "login" : "redirect:/";
    }
}
