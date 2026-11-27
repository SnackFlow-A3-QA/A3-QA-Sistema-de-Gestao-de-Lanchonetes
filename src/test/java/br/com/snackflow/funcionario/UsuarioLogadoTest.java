package br.com.snackflow.funcionario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsuarioLogadoTest {

    @Test
    void deveEntregarAutoridadeDoPerfil() {
        UsuarioLogado usuario = new UsuarioLogado(7, "Ana", "ana.caixa", "hash", Perfil.CAIXA, true);

        assertThat(usuario.getAuthorities()).extracting("authority").containsExactly("ROLE_CAIXA");
    }

    @Test
    void deveExporIdentificacaoDoFuncionario() {
        UsuarioLogado usuario = new UsuarioLogado(7, "Ana", "ana.caixa", "hash", Perfil.CAIXA, true);

        assertThat(usuario.getId()).isEqualTo(7);
        assertThat(usuario.getNome()).isEqualTo("Ana");
        assertThat(usuario.getUsername()).isEqualTo("ana.caixa");
        assertThat(usuario.getPassword()).isEqualTo("hash");
        assertThat(usuario.getPerfil()).isEqualTo(Perfil.CAIXA);
    }

    @Test
    void deveRefletirSituacaoAtivoNoEnabled() {
        UsuarioLogado ativo = new UsuarioLogado(1, "A", "a", "h", Perfil.ADMIN, true);
        UsuarioLogado inativo = new UsuarioLogado(2, "B", "b", "h", Perfil.ADMIN, false);

        assertThat(ativo.isEnabled()).isTrue();
        assertThat(inativo.isEnabled()).isFalse();
    }

    @Test
    void deveCriarAPartirDoFuncionario() {
        Funcionario funcionario = new Funcionario("Marcos", "Chapeiro", Perfil.COZINHA, "marcos", "hash");

        UsuarioLogado usuario = UsuarioLogado.de(funcionario);

        assertThat(usuario.getNome()).isEqualTo("Marcos");
        assertThat(usuario.getPerfil()).isEqualTo(Perfil.COZINHA);
        assertThat(usuario.isEnabled()).isTrue();
    }
}
