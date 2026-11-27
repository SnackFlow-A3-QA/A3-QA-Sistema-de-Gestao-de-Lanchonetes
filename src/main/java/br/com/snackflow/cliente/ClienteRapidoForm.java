package br.com.snackflow.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo JSON de POST /clientes/rapido. CPF é opcional; nome e telefone são obrigatórios. */
public record ClienteRapidoForm(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 100, message = "Nome muito longo.")
        String nome,
        @NotBlank(message = "Informe o telefone.")
        @Size(max = 30, message = "Telefone muito longo.")
        @TelefoneValido
        String telefone,
        @Size(max = 20, message = "CPF muito longo.")
        String cpf) {
}
