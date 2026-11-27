package br.com.snackflow.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegraNegocioExceptionTest {

    @Test
    void deveGuardarMensagemSegura() {
        RegraNegocioException excecao = new RegraNegocioException("Preço inválido");

        assertThat(excecao).hasMessage("Preço inválido");
    }

    @Test
    void deveGuardarCausaOriginal() {
        IllegalStateException causa = new IllegalStateException("erro do banco");

        RegraNegocioException excecao = new RegraNegocioException("Estoque insuficiente", causa);

        assertThat(excecao).hasMessage("Estoque insuficiente").hasCause(causa);
    }
}
