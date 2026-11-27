package br.com.snackflow.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Existe apenas no classpath de teste: serve de exemplo de tela com formulário e valida os
 * componentes de campo (campoTexto, campoTextarea, campoSelect, campoCheckbox) com erros reais.
 */
@Controller
class TelaDeFormularioTesteController {

    /** Form de exemplo: classe com getters e setters, como os Forms dos módulos. */
    public static class FormularioExemplo {
        @NotBlank
        private String nome;
        private String descricao;
        private Integer categoria;
        private boolean ativo = true;

        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getDescricao() { return descricao; }
        public void setDescricao(String descricao) { this.descricao = descricao; }
        public Integer getCategoria() { return categoria; }
        public void setCategoria(Integer categoria) { this.categoria = categoria; }
        public boolean isAtivo() { return ativo; }
        public void setAtivo(boolean ativo) { this.ativo = ativo; }
    }

    private static final String TELA = "teste/formulario-exemplo";

    @GetMapping("/teste/formulario")
    String exibir(Model modelo) {
        modelo.addAttribute("form", new FormularioExemplo());
        modelo.addAttribute("categorias", categorias());
        return TELA;
    }

    @PostMapping("/teste/formulario")
    String enviar(@Valid @ModelAttribute("form") FormularioExemplo form, BindingResult erros, Model modelo) {
        modelo.addAttribute("categorias", categorias());
        return TELA;
    }

    private Map<Integer, String> categorias() {
        Map<Integer, String> categorias = new LinkedHashMap<>();
        categorias.put(1, "Lanches");
        categorias.put(2, "Bebidas");
        return categorias;
    }
}
