package br.com.snackflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import org.junit.jupiter.api.Test;

class HomeELoginControllerTest {

    private final HomeController home = new HomeController();
    private final LoginController login = new LoginController();

    private UsuarioLogado usuario(Perfil perfil) {
        return new UsuarioLogado(1, "Teste", "teste", "hash", perfil, true);
    }

    @Test
    void homeDeveRedirecionarParaATelaInicialDoPerfil() {
        assertThat(home.inicio(usuario(Perfil.COZINHA))).isEqualTo("redirect:/cozinha");
        assertThat(home.inicio(usuario(Perfil.ADMIN))).isEqualTo("redirect:/pdv");
    }

    @Test
    void homeDeveUsarEmTodoPerfilAMesmaTelaInicialDeclaradaNoPerfil() {
        for (Perfil perfil : Perfil.values()) {
            assertThat(home.inicio(usuario(perfil))).isEqualTo("redirect:" + perfil.getPaginaInicial());
        }
    }

    @Test
    void homeDeveMandarAnonimoParaLogin() {
        assertThat(home.inicio(null)).isEqualTo("redirect:/login");
    }

    @Test
    void loginDeveExibirFormularioParaAnonimo() {
        assertThat(login.login(null)).isEqualTo("login");
    }

    @Test
    void loginDeveRedirecionarQuemJaEstaAutenticado() {
        assertThat(login.login(usuario(Perfil.CAIXA))).isEqualTo("redirect:/");
    }
}
