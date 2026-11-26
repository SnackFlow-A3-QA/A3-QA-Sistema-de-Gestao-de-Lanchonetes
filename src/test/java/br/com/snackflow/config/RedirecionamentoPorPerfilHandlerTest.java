package br.com.snackflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class RedirecionamentoPorPerfilHandlerTest {

    private final RedirecionamentoPorPerfilHandler handler = new RedirecionamentoPorPerfilHandler();

    @ParameterizedTest
    @CsvSource({"ADMIN,/pdv", "CAIXA,/pdv", "COZINHA,/cozinha", "ESTOQUE,/estoque"})
    void deveRedirecionarParaATelaInicialDoPerfil(Perfil perfil, String destino) throws IOException {
        UsuarioLogado usuario = new UsuarioLogado(1, "Teste", "teste", "hash", perfil, true);
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), resposta,
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));

        assertThat(resposta.getRedirectedUrl()).isEqualTo(destino);
    }

    @Test
    void deveIrParaRaizQuandoPrincipalNaoForUsuarioLogado() throws IOException {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), resposta,
                new UsernamePasswordAuthenticationToken("outro", null));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/");
    }
}
