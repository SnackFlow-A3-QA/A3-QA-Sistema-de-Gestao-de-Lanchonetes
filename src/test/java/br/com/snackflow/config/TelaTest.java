package br.com.snackflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.snackflow.funcionario.Perfil;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TelaTest {

    @Test
    void naoDeveTerRotaRepetida() {
        Set<String> rotas = Arrays.stream(Tela.values()).map(Tela::rota).collect(Collectors.toSet());

        assertThat(rotas).hasSize(Tela.values().length);
    }

    @Test
    void todaRotaDeveExigirPeloMenosUmPerfil() {
        assertThat(Tela.values()).allSatisfy(tela -> assertThat(tela.papeis()).isNotEmpty());
    }

    @Test
    void adminDeveAcessarTodasAsTelas() {
        assertThat(Tela.values()).allMatch(tela -> tela.permitidaPara(Perfil.ADMIN));
    }

    @Test
    void papeisDevemUsarNomeDoPerfilSemPrefixoRole() {
        assertThat(Tela.COZINHA_KDS.papeis()).containsExactlyInAnyOrder("COZINHA", "ADMIN");
    }

    @Test
    void telaComSecaoDeveAparecerNoMenuDoAdmin() {
        List<String> rotasDoMenu = new MenuService().montar(Perfil.ADMIN, "/").stream()
                .flatMap(secao -> secao.itens().stream()).map(MenuItem::rota).toList();

        assertThat(Arrays.stream(Tela.values()).filter(Tela::apareceNoMenu).map(Tela::rota))
                .isNotEmpty()
                .containsExactlyInAnyOrderElementsOf(rotasDoMenu);
        assertThat(Tela.PDV.apareceNoMenu()).isTrue();
    }

    @Test
    void clientesDeveSerAcessivelAoCaixaMasFicarForaDoMenu() {
        assertThat(Tela.CLIENTES.permitidaPara(Perfil.CAIXA)).isTrue();
        assertThat(Tela.CLIENTES.apareceNoMenu()).isFalse();
    }
}
