package br.com.snackflow.funcionario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PerfilTest {

    @ParameterizedTest
    @CsvSource({"ADMIN,/pdv", "CAIXA,/pdv", "COZINHA,/cozinha", "ESTOQUE,/estoque"})
    void deveInformarPaginaInicialDoPerfil(Perfil perfil, String esperada) {
        assertThat(perfil.getPaginaInicial()).isEqualTo(esperada);
    }

    @Test
    void deveMontarAutoridadeComPrefixoRole() {
        assertThat(Perfil.COZINHA.autoridade()).isEqualTo("ROLE_COZINHA");
    }
}
