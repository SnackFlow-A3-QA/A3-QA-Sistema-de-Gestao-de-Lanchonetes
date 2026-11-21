package br.com.snackflow.relatorio;

import br.com.snackflow.common.exception.RegraNegocioException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Tela 5 - Relatórios. */
@Controller
@RequestMapping("/relatorios")
public class RelatorioController {

    static final String VIEW = "relatorio/relatorios";

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    @GetMapping
    public String exibir(@RequestParam(defaultValue = "FATURAMENTO") TipoRelatorio tipo,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                         Model model) {
        ResultadoRelatorio resultado;
        try {
            resultado = service.gerar(tipo, inicio, fim);
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            resultado = new ResultadoRelatorio(tipo, inicio, fim, List.of(), List.of());
        }
        model.addAttribute("tipos", TipoRelatorio.values());
        model.addAttribute("resultado", resultado);
        return VIEW;
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(defaultValue = "FATURAMENTO") TipoRelatorio tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        byte[] csv = service.exportarCsv(service.gerar(tipo, inicio, fim));
        String arquivo = "relatorio-" + tipo.name().toLowerCase(Locale.ROOT) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + arquivo + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<String> regraNegocio(RegraNegocioException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
