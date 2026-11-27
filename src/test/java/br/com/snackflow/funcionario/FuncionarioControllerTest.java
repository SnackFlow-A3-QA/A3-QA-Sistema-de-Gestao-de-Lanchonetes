package br.com.snackflow.funcionario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.snackflow.common.RegraNegocioException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class FuncionarioControllerTest {

    @Mock
    private FuncionarioService servico;

    @InjectMocks
    private FuncionarioController controller;

    private final UsuarioLogado admin = new UsuarioLogado(1, "Carlos", "admin", "hash", Perfil.ADMIN, true);

    private BindingResult semErros(Object alvo) {
        return new BeanPropertyBindingResult(alvo, "form");
    }

    private BindingResult comErros(Object alvo) {
        BindingResult erros = new BeanPropertyBindingResult(alvo, "form");
        erros.reject("invalido");
        return erros;
    }

    @Test
    void deveListarComOIdDoUsuarioLogado() {
        when(servico.listar()).thenReturn(List.of());
        Model modelo = new ExtendedModelMap();

        assertThat(controller.listar(admin, modelo)).isEqualTo("funcionario/lista");
        assertThat(modelo.getAttribute("usuarioLogadoId")).isEqualTo(1);
        assertThat(modelo.getAttribute("funcionarios")).isEqualTo(List.of());
    }

    @Test
    void deveListarSemUsuarioAutenticado() {
        when(servico.listar()).thenReturn(List.of());
        Model modelo = new ExtendedModelMap();

        controller.listar(null, modelo);

        assertThat(modelo.getAttribute("usuarioLogadoId")).isNull();
    }

    @Test
    void deveExibirFormularioDeCadastroComPerfis() {
        Model modelo = new ExtendedModelMap();

        assertThat(controller.novo(modelo)).isEqualTo("funcionario/form");
        assertThat(modelo.getAttribute("edicao")).isEqualTo(false);
        assertThat(modelo.getAttribute("acao")).isEqualTo("/funcionarios/novo");
        assertThat(modelo.getAttribute("perfis")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKeys(Perfil.ADMIN, Perfil.CAIXA, Perfil.COZINHA, Perfil.ESTOQUE);
    }

    @Test
    void deveCadastrarERedirecionarComToast() {
        NovoFuncionarioForm form = new NovoFuncionarioForm();
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String destino = controller.cadastrar(form, semErros(form), new ExtendedModelMap(), flash);

        assertThat(destino).isEqualTo("redirect:/funcionarios");
        assertThat(flash.getFlashAttributes().get("ok")).isEqualTo("Funcionário cadastrado.");
        verify(servico).cadastrar(form);
    }

    @Test
    void deveReexibirFormularioEmErroDeValidacaoSemChamarOService() {
        NovoFuncionarioForm form = new NovoFuncionarioForm();
        Model modelo = new ExtendedModelMap();

        String destino = controller.cadastrar(form, comErros(form), modelo, new RedirectAttributesModelMap());

        assertThat(destino).isEqualTo("funcionario/form");
        assertThat(modelo.getAttribute("perfis")).isNotNull();
        verify(servico, never()).cadastrar(any());
    }

    @Test
    void deveMostrarMensagemDeRegraNoFormularioDeCadastro() {
        NovoFuncionarioForm form = new NovoFuncionarioForm();
        doThrow(new RegraNegocioException("Já existe um funcionário com este login.")).when(servico).cadastrar(form);
        Model modelo = new ExtendedModelMap();

        String destino = controller.cadastrar(form, semErros(form), modelo, new RedirectAttributesModelMap());

        assertThat(destino).isEqualTo("funcionario/form");
        assertThat(modelo.getAttribute("erro")).isEqualTo("Já existe um funcionário com este login.");
    }

    @Test
    void deveExibirFormularioDeEdicao() {
        FuncionarioForm form = new FuncionarioForm();
        when(servico.formularioDe(5)).thenReturn(form);
        Model modelo = new ExtendedModelMap();

        assertThat(controller.editar(5, modelo)).isEqualTo("funcionario/form");
        assertThat(modelo.getAttribute("form")).isSameAs(form);
        assertThat(modelo.getAttribute("edicao")).isEqualTo(true);
        assertThat(modelo.getAttribute("acao")).isEqualTo("/funcionarios/5/editar");
    }

    @Test
    void deveAtualizarERedirecionarComToast() {
        FuncionarioForm form = new FuncionarioForm();
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String destino = controller.atualizar(5, form, semErros(form), new ExtendedModelMap(), flash);

        assertThat(destino).isEqualTo("redirect:/funcionarios");
        assertThat(flash.getFlashAttributes().get("ok")).isEqualTo("Funcionário atualizado.");
        verify(servico).atualizar(5, form);
    }

    @Test
    void deveReexibirEdicaoEmErroDeValidacaoEDeRegra() {
        FuncionarioForm form = new FuncionarioForm();
        Model comErroDeCampo = new ExtendedModelMap();

        assertThat(controller.atualizar(5, form, comErros(form), comErroDeCampo, new RedirectAttributesModelMap()))
                .isEqualTo("funcionario/form");
        verify(servico, never()).atualizar(any(), any());

        doThrow(new RegraNegocioException("Login repetido")).when(servico).atualizar(5, form);
        Model comErroDeRegra = new ExtendedModelMap();

        assertThat(controller.atualizar(5, form, semErros(form), comErroDeRegra, new RedirectAttributesModelMap()))
                .isEqualTo("funcionario/form");
        assertThat(comErroDeRegra.getAttribute("erro")).isEqualTo("Login repetido");
        assertThat(comErroDeRegra.getAttribute("edicao")).isEqualTo(true);
    }

    @Test
    void deveExibirTelaDeSenhaComONomeDoFuncionario() {
        FuncionarioForm dados = new FuncionarioForm();
        dados.setNome("Julia");
        when(servico.formularioDe(5)).thenReturn(dados);
        Model modelo = new ExtendedModelMap();

        assertThat(controller.senha(5, modelo)).isEqualTo("funcionario/senha");
        assertThat(modelo.getAttribute("nomeFuncionario")).isEqualTo("Julia");
        assertThat(modelo.getAttribute("acao")).isEqualTo("/funcionarios/5/senha");
        assertThat(modelo.getAttribute("form")).isInstanceOf(SenhaForm.class);
    }

    @Test
    void deveRedefinirSenhaERedirecionarComToast() {
        SenhaForm form = new SenhaForm();
        form.setSenha("nova-senha");
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String destino = controller.redefinirSenha(5, form, semErros(form), new ExtendedModelMap(), flash);

        assertThat(destino).isEqualTo("redirect:/funcionarios");
        assertThat(flash.getFlashAttributes().get("ok")).isEqualTo("Senha redefinida.");
        verify(servico).redefinirSenha(5, "nova-senha");
    }

    @Test
    void deveReexibirTelaDeSenhaEmErroDeValidacao() {
        FuncionarioForm dados = new FuncionarioForm();
        dados.setNome("Julia");
        when(servico.formularioDe(5)).thenReturn(dados);
        SenhaForm form = new SenhaForm();
        Model modelo = new ExtendedModelMap();

        assertThat(controller.redefinirSenha(5, form, comErros(form), modelo, new RedirectAttributesModelMap()))
                .isEqualTo("funcionario/senha");
        assertThat(modelo.getAttribute("nomeFuncionario")).isEqualTo("Julia");
        verify(servico, never()).redefinirSenha(any(), any());
    }

    @Test
    void deveMostrarMensagemDeRegraNaTelaDeSenha() {
        FuncionarioForm dados = new FuncionarioForm();
        dados.setNome("Julia");
        when(servico.formularioDe(5)).thenReturn(dados);
        SenhaForm form = new SenhaForm();
        doThrow(new RegraNegocioException("Senha ruim")).when(servico).redefinirSenha(5, null);
        Model modelo = new ExtendedModelMap();

        assertThat(controller.redefinirSenha(5, form, semErros(form), modelo, new RedirectAttributesModelMap()))
                .isEqualTo("funcionario/senha");
        assertThat(modelo.getAttribute("erro")).isEqualTo("Senha ruim");
    }

    @Test
    void deveMostrarErroQuandoOFuncionarioDaSenhaNaoExiste() {
        when(servico.formularioDe(99)).thenThrow(new RegraNegocioException("Funcionário não encontrado."));
        SenhaForm form = new SenhaForm();
        Model modelo = new ExtendedModelMap();

        controller.redefinirSenha(99, form, comErros(form), modelo, new RedirectAttributesModelMap());

        assertThat(modelo.getAttribute("erro")).isEqualTo("Funcionário não encontrado.");
        assertThat(modelo.getAttribute("nomeFuncionario")).isEqualTo("");
    }

    @Test
    void deveInativarEReativarComToast() {
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        assertThat(controller.inativar(5, admin, flash)).isEqualTo("redirect:/funcionarios");
        assertThat(flash.getFlashAttributes().get("ok")).isEqualTo("Funcionário inativado.");
        verify(servico).alterarSituacao(5, false, admin);

        RedirectAttributesModelMap flash2 = new RedirectAttributesModelMap();
        controller.ativar(5, admin, flash2);
        assertThat(flash2.getFlashAttributes().get("ok")).isEqualTo("Funcionário reativado.");
        verify(servico).alterarSituacao(5, true, admin);
    }

    @Test
    void deveMostrarErroAoInativarAPropriaConta() {
        doThrow(new RegraNegocioException("Você não pode inativar a sua própria conta."))
                .when(servico).alterarSituacao(1, false, admin);
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        assertThat(controller.inativar(1, admin, flash)).isEqualTo("redirect:/funcionarios");
        assertThat(flash.getFlashAttributes().get("erro")).isEqualTo("Você não pode inativar a sua própria conta.");
        assertThat(flash.getFlashAttributes()).doesNotContainKey("ok");
    }
}
