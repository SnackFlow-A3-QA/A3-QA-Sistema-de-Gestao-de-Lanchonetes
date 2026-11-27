package br.com.snackflow.cliente;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de clientes usada pelo modal do PDV (contrato da seção 14 do guia). Erros saem pelo
 * {@code TratadorJsonDeExcecoes}; o perfil (CAIXA, ADMIN) vem de {@code Tela.CLIENTES}.
 */
@RestController
@RequestMapping("/clientes")
class ClienteApiController {

    private final ClienteService servico;

    ClienteApiController(ClienteService servico) {
        this.servico = servico;
    }

    @GetMapping("/busca")
    List<ClienteResposta> buscar(@RequestParam(name = "q", required = false) String q) {
        return servico.buscar(q);
    }

    @PostMapping("/rapido")
    ResponseEntity<ClienteResposta> cadastrarRapido(@Valid @RequestBody ClienteRapidoForm form) {
        return ResponseEntity.status(201).body(servico.cadastrarRapido(form));
    }

    @PutMapping("/{id}")
    ClienteResposta atualizar(@PathVariable("id") Integer id, @Valid @RequestBody ClienteRapidoForm form) {
        return servico.atualizar(id, form);
    }

    @PostMapping("/{id}/inativar")
    ResponseEntity<Void> inativar(@PathVariable("id") Integer id) {
        servico.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
