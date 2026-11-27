package br.com.snackflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.snackflow.funcionario.Perfil;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuServiceTest {

    private final MenuService servico = new MenuService();

    private List<String> rotas(List<MenuSecao> secoes) {
        return secoes.stream().flatMap(secao -> secao.itens().stream()).map(MenuItem::rota).toList();
    }

    @Test
    void adminDeveVerTodosOsItensEmDuasSecoes() {
        List<MenuSecao> secoes = servico.montar(Perfil.ADMIN, "/pdv");

        assertThat(secoes).extracting(MenuSecao::titulo).containsExactly("OPERAÇÃO", "ADMINISTRAÇÃO");
        assertThat(rotas(secoes)).containsExactly(
                "/pdv", "/cozinha", "/estoque", "/produtos", "/funcionarios", "/relatorios");
    }

    @Test
    void caixaDeveVerApenasPdvEOcultarAdministracao() {
        List<MenuSecao> secoes = servico.montar(Perfil.CAIXA, "/pdv");

        assertThat(secoes).extracting(MenuSecao::titulo).containsExactly("OPERAÇÃO");
        assertThat(rotas(secoes)).containsExactly("/pdv");
    }

    @Test
    void cozinhaDeveVerApenasCozinha() {
        assertThat(rotas(servico.montar(Perfil.COZINHA, "/cozinha"))).containsExactly("/cozinha");
    }

    @Test
    void estoqueDeveVerApenasEstoque() {
        assertThat(rotas(servico.montar(Perfil.ESTOQUE, "/estoque"))).containsExactly("/estoque");
    }

    @Test
    void deveMarcarComoAtivoOItemDaRotaAtualEDeSubrotas() {
        List<MenuSecao> secoes = servico.montar(Perfil.ADMIN, "/produtos/novo");

        List<String> ativos = secoes.stream().flatMap(secao -> secao.itens().stream())
                .filter(MenuItem::ativo).map(MenuItem::rota).toList();
        assertThat(ativos).containsExactly("/produtos");
    }

    @Test
    void naoDeveMarcarRotaQueApenasComecaIgual() {
        List<MenuSecao> secoes = servico.montar(Perfil.ADMIN, "/pdvx");

        assertThat(secoes).flatExtracting(MenuSecao::itens).noneMatch(MenuItem::ativo);
    }

    @Test
    void deveAceitarCaminhoNulo() {
        List<MenuSecao> secoes = servico.montar(Perfil.CAIXA, null);

        assertThat(secoes).flatExtracting(MenuSecao::itens).noneMatch(MenuItem::ativo);
    }

    @Test
    void todaTelaComSecaoPermitidaAoPerfilDeveAparecerNoMenu() {
        for (Perfil perfil : Perfil.values()) {
            List<String> esperadas = Arrays.stream(Tela.values())
                    .filter(Tela::apareceNoMenu)
                    .filter(tela -> tela.permitidaPara(perfil))
                    .map(Tela::rota)
                    .toList();

            assertThat(rotas(servico.montar(perfil, "/"))).as("menu de %s", perfil)
                    .containsExactlyInAnyOrderElementsOf(esperadas);
        }
    }

    @Test
    void telaSemSecaoNaoDeveAparecerNoMenuMesmoPermitidaAoPerfil() {
        assertThat(Tela.CLIENTES.apareceNoMenu()).isFalse();
        for (Perfil perfil : List.of(Perfil.CAIXA, Perfil.ADMIN)) {
            assertThat(Tela.CLIENTES.permitidaPara(perfil)).isTrue();
            assertThat(rotas(servico.montar(perfil, "/clientes"))).doesNotContain("/clientes");
        }
    }
}
