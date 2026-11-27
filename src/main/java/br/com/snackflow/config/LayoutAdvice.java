package br.com.snackflow.config;

import br.com.snackflow.funcionario.UsuarioLogado;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Coloca em TODO modelo os dados que o layout precisa (usuário, perfil e menu).
 * Os controllers dos módulos não precisam repassar nada disso.
 *
 * <p>Atributos: {@code usuarioNome}, {@code usuarioPerfil} e {@code menuSecoes}.
 */
@ControllerAdvice
class LayoutAdvice {

    private final MenuService menuService;

    LayoutAdvice(MenuService menuService) {
        this.menuService = menuService;
    }

    @ModelAttribute
    void dadosDoLayout(Authentication autenticacao, HttpServletRequest requisicao, Model modelo) {
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioLogado usuario)) {
            return;
        }
        String caminho = requisicao.getRequestURI().substring(requisicao.getContextPath().length());
        modelo.addAttribute("usuarioNome", usuario.getNome());
        modelo.addAttribute("usuarioPerfil", usuario.getPerfil().name());
        modelo.addAttribute("menuSecoes", menuService.montar(usuario.getPerfil(), caminho));
    }
}
