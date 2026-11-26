package br.com.snackflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

class LayoutAdviceTest {

    private final LayoutAdvice advice = new LayoutAdvice(new MenuService());

    private MockHttpServletRequest requisicao(String uri) {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.setRequestURI(uri);
        return requisicao;
    }

    @Test
    void deveColocarUsuarioEMenuNoModelo() {
        UsuarioLogado usuario = new UsuarioLogado(1, "Ana Paula", "ana", "hash", Perfil.CAIXA, true);
        Model modelo = new ExtendedModelMap();

        advice.dadosDoLayout(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()),
                requisicao("/pdv"), modelo);

        assertThat(modelo.getAttribute("usuarioNome")).isEqualTo("Ana Paula");
        assertThat(modelo.getAttribute("usuarioPerfil")).isEqualTo("CAIXA");
        assertThat(modelo.getAttribute("menuSecoes")).isNotNull();
    }

    @Test
    void deveRemoverCaminhoDeContextoAoMarcarItemAtivo() {
        UsuarioLogado usuario = new UsuarioLogado(1, "Ana", "ana", "hash", Perfil.CAIXA, true);
        MockHttpServletRequest requisicao = requisicao("/snackflow/pdv");
        requisicao.setContextPath("/snackflow");
        Model modelo = new ExtendedModelMap();

        advice.dadosDoLayout(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()),
                requisicao, modelo);

        @SuppressWarnings("unchecked")
        java.util.List<MenuSecao> menu = (java.util.List<MenuSecao>) modelo.getAttribute("menuSecoes");
        assertThat(menu).flatExtracting(MenuSecao::itens).anyMatch(MenuItem::ativo);
    }

    @Test
    void naoDeveAlterarOModeloSemUsuarioAutenticado() {
        Model modelo = new ExtendedModelMap();

        advice.dadosDoLayout(null, requisicao("/login"), modelo);

        assertThat(modelo.asMap()).isEmpty();
    }

    @Test
    void naoDeveAlterarOModeloQuandoPrincipalNaoForUsuarioLogado() {
        Model modelo = new ExtendedModelMap();

        advice.dadosDoLayout(new UsernamePasswordAuthenticationToken("anonimo", null), requisicao("/x"), modelo);

        assertThat(modelo.asMap()).isEmpty();
    }
}
