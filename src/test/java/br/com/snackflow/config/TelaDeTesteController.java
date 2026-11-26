package br.com.snackflow.config;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller existente apenas no classpath de teste: renderiza o template
 * teste/layout-exemplo, que usa o layout e todos os fragmentos.
 */
@Controller
class TelaDeTesteController {

    @GetMapping("/teste/layout")
    String tela(Model modelo) {
        modelo.addAttribute("ok", "Registro salvo");
        modelo.addAttribute("erro", "Falha de exemplo");
        return "teste/layout-exemplo";
    }
}
