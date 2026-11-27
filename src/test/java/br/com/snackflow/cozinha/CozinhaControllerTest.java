package br.com.snackflow.cozinha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.snackflow.common.RegraNegocioException;
import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class CozinhaControllerTest {

    @Mock
    private CozinhaService service;

    @InjectMocks
    private CozinhaController controller;

    @Test
    void exibirFilaDevePreencherModeloERetornarTemplateDaCozinha() {
        when(service.listarFilaOrdenada()).thenReturn(List.of());
        Model modelo = new ExtendedModelMap();

        String view = controller.exibirFila(modelo);

        assertThat(view).isEqualTo("cozinha/fila");
        assertThat(modelo.getAttribute("pedidos")).isEqualTo(List.of());
    }

    @Test
    void marcarComoProntoDeveUsarFuncionarioLogadoEDefinirFlashOk() {
        UsuarioLogado cozinha = new UsuarioLogado(3, "Marcos", "marcos.cozinha", "hash", Perfil.COZINHA, true);
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String view = controller.marcarComoPronto(18, cozinha, flash);

        assertThat(view).isEqualTo("redirect:/cozinha");
        verify(service).marcarComoPronto(18, 3);
        assertThat(flash.getFlashAttributes().get("ok")).isEqualTo("Pedido #18 marcado como pronto!");
    }

    @Test
    void tratarRegraNegocioDeveDevolverFlashErroERedirectParaCozinha() {
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = controller.tratarRegraNegocio(new RegraNegocioException("Status invalido"), flash);

        assertThat(view).isEqualTo("redirect:/cozinha");
        assertThat(flash.getFlashAttributes().get("erro")).isEqualTo("Status invalido");
    }
}
