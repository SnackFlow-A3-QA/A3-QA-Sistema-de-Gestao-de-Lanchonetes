package br.com.snackflow.funcionario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FuncionarioTest {

    @Test
    void deveNascerAtivoComOsDadosInformados() {
        Funcionario funcionario = new Funcionario("Julia", "Estoquista", Perfil.ESTOQUE, "julia", "hash");

        assertThat(funcionario.getNome()).isEqualTo("Julia");
        assertThat(funcionario.getCargo()).isEqualTo("Estoquista");
        assertThat(funcionario.getPerfil()).isEqualTo(Perfil.ESTOQUE);
        assertThat(funcionario.getLogin()).isEqualTo("julia");
        assertThat(funcionario.getSenhaHash()).isEqualTo("hash");
        assertThat(funcionario.getId()).isNull();
        assertThat(funcionario.isAtivo()).isTrue();
    }

    @Test
    void devePermitirInativar() {
        Funcionario funcionario = new Funcionario("Julia", "Estoquista", Perfil.ESTOQUE, "julia", "hash");

        funcionario.setAtivo(false);

        assertThat(funcionario.isAtivo()).isFalse();
    }
}
