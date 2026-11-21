package br.com.snackflow.cliente;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

    @Test
    void validaCpfsCorretos() {
        assertThat(Cpf.valido("52998224725")).isTrue();
        assertThat(Cpf.valido("11144477735")).isTrue();
    }

    @Test
    void rejeitaCpfsInvalidos() {
        assertThat(Cpf.valido(null)).isFalse();
        assertThat(Cpf.valido("123")).isFalse();
        assertThat(Cpf.valido("529.982.247-25")).isFalse(); // precisa vir só com dígitos
        assertThat(Cpf.valido("11111111111")).isFalse();    // sequência repetida
        assertThat(Cpf.valido("52998224726")).isFalse();    // 2º dígito errado
        assertThat(Cpf.valido("52998224715")).isFalse();    // 1º dígito errado
    }

    @Test
    void removeFormatacao() {
        assertThat(Cpf.somenteDigitos("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Cpf.somenteDigitos(null)).isEmpty();
        assertThat(Cpf.somenteDigitos("Maria")).isEmpty();
    }

    @Test
    void formataCpf() {
        assertThat(Cpf.formatar("52998224725")).isEqualTo("529.982.247-25");
        assertThat(Cpf.formatar(null)).isEmpty();
        assertThat(Cpf.formatar("123")).isEqualTo("123");
    }
}
