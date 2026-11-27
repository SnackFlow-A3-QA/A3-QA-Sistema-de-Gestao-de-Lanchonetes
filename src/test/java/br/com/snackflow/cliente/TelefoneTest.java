package br.com.snackflow.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TelefoneTest {

    @ParameterizedTest
    @CsvSource({"4898765432,(48) 9876-5432", "48987654321,(48) 98765-4321", "'(48) 98765-4321',(48) 98765-4321",
            "'48 9876-5432',(48) 9876-5432", "'11 9 1234 5678',(11) 91234-5678"})
    void deveFormatarNoPadraoCanonico(String entrada, String esperado) {
        assertThat(Telefone.formatar(entrada)).contains(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123", "489876543", "489876543210", "0498765432", "04987654321",
            "48887654321", "abcdefghij"})
    void deveRecusarInvalido(String entrada) {
        assertThat(Telefone.formatar(entrada)).isEmpty();
    }

    @Test
    void deveRecusarNulo() {
        assertThat(Telefone.formatar(null)).isEmpty();
    }
}
