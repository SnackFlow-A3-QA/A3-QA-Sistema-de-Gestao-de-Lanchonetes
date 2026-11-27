package br.com.snackflow.config;

import br.com.snackflow.funcionario.UsuarioLogado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * A raiz "/" leva cada perfil para a sua tela inicial.
 *
 * <p>Os destinos são literais fixos (nada vindo do usuário entra na URL), o que evita o alerta de
 * redirecionamento aberto (Sonar javasecurity:S5146). {@code HomeELoginControllerTest} garante que
 * continuam iguais a {@code Perfil#getPaginaInicial()}.
 */
@Controller
class HomeController {

    @GetMapping("/")
    String inicio(@AuthenticationPrincipal UsuarioLogado usuario) {
        if (usuario == null) {
            return "redirect:/login";
        }
        return switch (usuario.getPerfil()) {
            case ADMIN, CAIXA -> "redirect:/pdv";
            case COZINHA -> "redirect:/cozinha";
            case ESTOQUE -> "redirect:/estoque";
        };
    }
}
