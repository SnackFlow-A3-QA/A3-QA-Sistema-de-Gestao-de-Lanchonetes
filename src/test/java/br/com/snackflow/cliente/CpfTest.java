package br.com.snackflow.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"39053344705", "11144477735", "52998224725"})
    void deveAceitarCpfValido(String cpf) {
        assertThat(Cpf.valido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11111111111", "00000000000", "39053344706", "39053344715", "123", "3905334470",
            "390533447055", "3905334470a"})
    void deveRecusarCpfInvalido(String cpf) {
        assertThat(Cpf.valido(cpf)).isFalse();
    }

    @Test
    void deveRecusarNulo() {
        assertThat(Cpf.valido(null)).isFalse();
    }

    @Test
    void deveRemoverPontuacao() {
        assertThat(Cpf.somenteDigitos("390.533.447-05")).isEqualTo("39053344705");
        assertThat(Cpf.somenteDigitos(null)).isEmpty();
    }
}
