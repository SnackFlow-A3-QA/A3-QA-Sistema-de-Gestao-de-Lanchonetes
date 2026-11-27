package br.com.snackflow.funcionario;

import br.com.snackflow.common.RegraNegocioException;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas de funcionários (/funcionarios, perfil ADMIN definido em Tela.FUNCIONARIOS). */
@Controller
@RequestMapping("/funcionarios")
class FuncionarioController {

    private static final String TELA_LISTA = "funcionario/lista";
    private static final String TELA_FORM = "funcionario/form";
    private static final String TELA_SENHA = "funcionario/senha";
    private static final String REDIRECT_LISTA = "redirect:/funcionarios";

    private final FuncionarioService servico;

    FuncionarioController(FuncionarioService servico) {
        this.servico = servico;
    }

    @GetMapping
    String listar(@AuthenticationPrincipal UsuarioLogado usuario, Model modelo) {
        modelo.addAttribute("funcionarios", servico.listar());
        modelo.addAttribute("usuarioLogadoId", usuario == null ? null : usuario.getId());
        return TELA_LISTA;
    }

    @GetMapping("/novo")
    String novo(Model modelo) {
        modelo.addAttribute("form", new NovoFuncionarioForm());
        prepararFormulario(modelo, false, "/funcionarios/novo");
        return TELA_FORM;
    }

    @PostMapping("/novo")
    String cadastrar(@Valid @ModelAttribute("form") NovoFuncionarioForm form, BindingResult erros,
                     Model modelo, RedirectAttributes flash) {
        if (erros.hasErrors()) {
            prepararFormulario(modelo, false, "/funcionarios/novo");
            return TELA_FORM;
        }
        try {
            servico.cadastrar(form);
        } catch (RegraNegocioException erro) {
            modelo.addAttribute("erro", erro.getMessage());
            prepararFormulario(modelo, false, "/funcionarios/novo");
            return TELA_FORM;
        }
        flash.addFlashAttribute("ok", "Funcionário cadastrado.");
        return REDIRECT_LISTA;
    }

    @GetMapping("/{id}/editar")
    String editar(@PathVariable("id") Integer id, Model modelo) {
        modelo.addAttribute("form", servico.formularioDe(id));
        prepararFormulario(modelo, true, "/funcionarios/" + id + "/editar");
        return TELA_FORM;
    }

    @PostMapping("/{id}/editar")
    String atualizar(@PathVariable("id") Integer id, @Valid @ModelAttribute("form") FuncionarioForm form,
                     BindingResult erros, Model modelo, RedirectAttributes flash) {
        String acao = "/funcionarios/" + id + "/editar";
        if (erros.hasErrors()) {
            prepararFormulario(modelo, true, acao);
            return TELA_FORM;
        }
        try {
            servico.atualizar(id, form);
        } catch (RegraNegocioException erro) {
            modelo.addAttribute("erro", erro.getMessage());
            prepararFormulario(modelo, true, acao);
            return TELA_FORM;
        }
        flash.addFlashAttribute("ok", "Funcionário atualizado.");
        return REDIRECT_LISTA;
    }

    @GetMapping("/{id}/senha")
    String senha(@PathVariable("id") Integer id, Model modelo) {
        modelo.addAttribute("nomeFuncionario", servico.formularioDe(id).getNome());
        modelo.addAttribute("form", new SenhaForm());
        modelo.addAttribute("acao", "/funcionarios/" + id + "/senha");
        return TELA_SENHA;
    }

    @PostMapping("/{id}/senha")
    String redefinirSenha(@PathVariable("id") Integer id, @Valid @ModelAttribute("form") SenhaForm form,
                          BindingResult erros, Model modelo, RedirectAttributes flash) {
        String acao = "/funcionarios/" + id + "/senha";
        if (erros.hasErrors()) {
            return telaDeSenha(id, acao, modelo, null);
        }
        try {
            servico.redefinirSenha(id, form.getSenha());
        } catch (RegraNegocioException erro) {
            return telaDeSenha(id, acao, modelo, erro.getMessage());
        }
        flash.addFlashAttribute("ok", "Senha redefinida.");
        return REDIRECT_LISTA;
    }

    @PostMapping("/{id}/inativar")
    String inativar(@PathVariable("id") Integer id, @AuthenticationPrincipal UsuarioLogado usuario,
                    RedirectAttributes flash) {
        return alterarSituacao(id, false, usuario, flash, "Funcionário inativado.");
    }

    @PostMapping("/{id}/ativar")
    String ativar(@PathVariable("id") Integer id, @AuthenticationPrincipal UsuarioLogado usuario,
                  RedirectAttributes flash) {
        return alterarSituacao(id, true, usuario, flash, "Funcionário reativado.");
    }

    private String alterarSituacao(Integer id, boolean ativo, UsuarioLogado usuario, RedirectAttributes flash,
                                   String mensagemOk) {
        try {
            servico.alterarSituacao(id, ativo, usuario);
            flash.addFlashAttribute("ok", mensagemOk);
        } catch (RegraNegocioException erro) {
            flash.addFlashAttribute("erro", erro.getMessage());
        }
        return REDIRECT_LISTA;
    }

    private String telaDeSenha(Integer id, String acao, Model modelo, String erro) {
        String nome;
        try {
            nome = servico.formularioDe(id).getNome();
        } catch (RegraNegocioException naoEncontrado) {
            nome = "";
            erro = naoEncontrado.getMessage();
        }
        modelo.addAttribute("nomeFuncionario", nome);
        modelo.addAttribute("acao", acao);
        if (erro != null) {
            modelo.addAttribute("erro", erro);
        }
        return TELA_SENHA;
    }

    private void prepararFormulario(Model modelo, boolean edicao, String acao) {
        modelo.addAttribute("edicao", edicao);
        modelo.addAttribute("acao", acao);
        modelo.addAttribute("perfis", perfis());
    }

    private Map<Perfil, String> perfis() {
        Map<Perfil, String> opcoes = new LinkedHashMap<>();
        opcoes.put(Perfil.ADMIN, "Administrador");
        opcoes.put(Perfil.CAIXA, "Caixa");
        opcoes.put(Perfil.COZINHA, "Cozinha");
        opcoes.put(Perfil.ESTOQUE, "Estoque");
        return opcoes;
    }
}
