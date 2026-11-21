package br.com.snackflow.cliente;

import br.com.snackflow.common.exception.RegraNegocioException;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Tela 4 - Gestão de Clientes. */
@Controller
@RequestMapping("/clientes")
public class ClienteController {

    static final String VIEW = "cliente/clientes";
    private static final String REDIRECT = "redirect:/clientes";

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @GetMapping
    public String listar(@RequestParam(defaultValue = "") String busca,
                         @RequestParam(defaultValue = "false") boolean inativos,
                         @RequestParam(required = false) Integer editar,
                         Model model) {
        ClienteForm form = new ClienteForm();
        if (editar != null) {
            try {
                form = ClienteForm.de(service.buscar(editar));
            } catch (RegraNegocioException e) {
                model.addAttribute("erro", e.getMessage());
            }
        }
        preencher(model, form, busca, inativos);
        return VIEW;
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("form") ClienteForm form,
                         BindingResult resultado,
                         Model model,
                         RedirectAttributes redirect) {
        if (!resultado.hasErrors()) {
            try {
                boolean novo = form.getId() == null;
                service.salvar(form);
                redirect.addFlashAttribute("ok", novo ? "Cliente cadastrado." : "Cliente atualizado.");
                return REDIRECT;
            } catch (RegraNegocioException e) {
                model.addAttribute("erro", e.getMessage());
            } catch (DataAccessException | TransactionException e) {
                model.addAttribute("erro", "Não foi possível salvar o cliente. Verifique os dados e tente novamente.");
            }
        }
        preencher(model, form, "", false);
        return VIEW;
    }

    @PostMapping("/{id}/situacao")
    public String alterarSituacao(@PathVariable Integer id,
                                  @RequestParam boolean ativo,
                                  RedirectAttributes redirect) {
        try {
            service.alterarSituacao(id, ativo);
            redirect.addFlashAttribute("ok", ativo ? "Cliente reativado." : "Cliente inativado.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return REDIRECT;
    }

    private void preencher(Model model, ClienteForm form, String busca, boolean inativos) {
        model.addAttribute("form", form);
        model.addAttribute("clientes", service.pesquisar(busca, !inativos));
        model.addAttribute("busca", busca);
        model.addAttribute("inativos", inativos);
    }
}
