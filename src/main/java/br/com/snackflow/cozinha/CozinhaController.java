package br.com.snackflow.cozinha;

import br.com.snackflow.common.RegraNegocioException;
import br.com.snackflow.funcionario.UsuarioLogado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Controller web do display da Cozinha (/cozinha). Perfis COZINHA e ADMIN (config/Tela). */
@Controller
@RequestMapping("/cozinha")
public class CozinhaController {

    private static final String REDIRECT_COZINHA = "redirect:/cozinha";

    private final CozinhaService service;

    public CozinhaController(CozinhaService service) {
        this.service = service;
    }

    @GetMapping
    public String exibirFila(Model modelo) {
        modelo.addAttribute("pedidos", service.listarFilaOrdenada());
        return "cozinha/fila";
    }

    @PostMapping("/pedidos/{id}/pronto")
    public String marcarComoPronto(
            @PathVariable Integer id,
            @AuthenticationPrincipal UsuarioLogado usuario,
            RedirectAttributes flash) {
        Integer idFuncionario = usuario == null ? null : usuario.getId();
        service.marcarComoPronto(id, idFuncionario);
        flash.addFlashAttribute("ok", "Pedido #" + id + " marcado como pronto!");
        return REDIRECT_COZINHA;
    }

    @ExceptionHandler(RegraNegocioException.class)
    String tratarRegraNegocio(RegraNegocioException erro, RedirectAttributes flash) {
        flash.addFlashAttribute("erro", erro.getMessage());
        return REDIRECT_COZINHA;
    }
}
