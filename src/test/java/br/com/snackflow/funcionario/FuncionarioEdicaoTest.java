package br.com.snackflow.funcionario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FuncionarioEdicaoTest {

    @Test
    void deveAtualizarDadosCadastraisSemMexerNaSenhaNemNaSituacao() {
        Funcionario funcionario = new Funcionario("Julia", "Estoquista", Perfil.ESTOQUE, "julia", "hash");

        funcionario.atualizarDados("Julia Mendes", "Supervisora", Perfil.ADMIN, "julia.m");

        assertThat(funcionario.getNome()).isEqualTo("Julia Mendes");
        assertThat(funcionario.getCargo()).isEqualTo("Supervisora");
        assertThat(funcionario.getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(funcionario.getLogin()).isEqualTo("julia.m");
        assertThat(funcionario.getSenhaHash()).isEqualTo("hash");
        assertThat(funcionario.isAtivo()).isTrue();
    }

    @Test
    void deveRedefinirSenhaSoComOHash() {
        Funcionario funcionario = new Funcionario("Julia", "Estoquista", Perfil.ESTOQUE, "julia", "hash");

        funcionario.redefinirSenha("novo-hash");

        assertThat(funcionario.getSenhaHash()).isEqualTo("novo-hash");
        assertThat(funcionario.getLogin()).isEqualTo("julia");
    }
}
