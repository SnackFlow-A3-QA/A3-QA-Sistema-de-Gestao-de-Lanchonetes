package br.com.snackflow.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClienteRapidoFormTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> mensagens(ClienteRapidoForm form) {
        return validador.validate(form).stream().map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void deveAceitarFormularioValidoComECpfVazio() {
        assertThat(mensagens(new ClienteRapidoForm("Ana", "(48) 98765-4321", null))).isEmpty();
    }

    @Test
    void deveExigirNomeETelefone() {
        assertThat(mensagens(new ClienteRapidoForm(" ", "", null)))
                .containsExactlyInAnyOrder("Informe o nome.", "Informe o telefone.");
    }

    @Test
    void deveRecusarTelefoneMalformado() {
        assertThat(mensagens(new ClienteRapidoForm("Ana", "123", null))).containsExactly("Telefone inválido.");
    }
}
