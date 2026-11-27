package br.com.snackflow.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class TratadorGlobalDeExcecoesTest {

    private final TratadorGlobalDeExcecoes tratador = new TratadorGlobalDeExcecoes();

    private MockHttpServletRequest requisicao(String contexto, String referer) {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.setContextPath(contexto);
        if (referer != null) {
            requisicao.addHeader("Referer", referer);
        }
        return requisicao;
    }

    @Test
    void deveVoltarParaATelaDeOrigemComAMensagemNoFlash() {
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String destino = tratador.regraDeNegocio(new RegraNegocioException("Preço inválido"),
                requisicao("", "http://localhost:8080/produtos/novo?x=1"), flash);

        assertThat(destino).isEqualTo("redirect:/produtos/novo");
        assertThat(flash.getFlashAttributes().get("erro")).isEqualTo("Preço inválido");
    }

    @Test
    void deveRemoverOCaminhoDeContextoAoVoltar() {
        String destino = tratador.regraDeNegocio(new RegraNegocioException("x"),
                requisicao("/snackflow", "http://localhost/snackflow/estoque"), new RedirectAttributesModelMap());

        assertThat(destino).isEqualTo("redirect:/estoque");
    }

    @Test
    void deveIrParaARaizSemReferer() {
        String destino = tratador.regraDeNegocio(new RegraNegocioException("x"),
                requisicao("", null), new RedirectAttributesModelMap());

        assertThat(destino).isEqualTo("redirect:/");
    }

    @Test
    void deveIrParaARaizComRefererEmBranco() {
        String destino = tratador.regraDeNegocio(new RegraNegocioException("x"),
                requisicao("", "  "), new RedirectAttributesModelMap());

        assertThat(destino).isEqualTo("redirect:/");
    }

    @Test
    void deveIrParaARaizComRefererMalformado() {
        String destino = tratador.regraDeNegocio(new RegraNegocioException("x"),
                requisicao("", "http://[invalido"), new RedirectAttributesModelMap());

        assertThat(destino).isEqualTo("redirect:/");
    }

    @Test
    void deveIgnorarRefererForaDoContextoDaAplicacao() {
        String destino = tratador.regraDeNegocio(new RegraNegocioException("x"),
                requisicao("/snackflow", "http://localhost/outra-app/tela"), new RedirectAttributesModelMap());

        assertThat(destino).isEqualTo("redirect:/");
    }

    @Test
    void deveVoltarParaARaizQuandoOReferenciaTemSoOContexto() {
        String destino = tratador.regraDeNegocio(new RegraNegocioException("x"),
                requisicao("/snackflow", "http://localhost/snackflow"), new RedirectAttributesModelMap());

        assertThat(destino).isEqualTo("redirect:/");
    }

    @Test
    void deveMostrarMensagemDoTriggerEmErroDeBanco() {
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        DataIntegrityViolationException erro =
                new DataIntegrityViolationException("falha", new SQLException("Estoque insuficiente", "45000"));

        String destino = tratador.erroDeBanco(erro, requisicao("", "http://localhost/pdv"), flash);

        assertThat(destino).isEqualTo("redirect:/pdv");
        assertThat(flash.getFlashAttributes().get("erro")).isEqualTo("Estoque insuficiente");
    }

    @Test
    void naoDeveVazarDetalhesTecnicosEmErroDeBanco() {
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        DataIntegrityViolationException erro =
                new DataIntegrityViolationException("falha", new SQLException("tabela x quebrou", "42000"));

        tratador.erroDeBanco(erro, requisicao("", null), flash);

        assertThat(flash.getFlashAttributes().get("erro")).isEqualTo(ErrosDoBanco.MENSAGEM_GENERICA);
    }
}
