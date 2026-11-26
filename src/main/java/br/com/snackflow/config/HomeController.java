package br.com.snackflow.config;

import br.com.snackflow.funcionario.UsuarioLogado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** A raiz "/" leva cada perfil para a sua tela inicial. */
@Controller
class HomeController {

    @GetMapping("/")
    String inicio(@AuthenticationPrincipal UsuarioLogado usuario) {
        if (usuario == null) {
            return "redirect:/login";
        }
        return "redirect:" + usuario.getPerfil().getPaginaInicial();
    }
}
