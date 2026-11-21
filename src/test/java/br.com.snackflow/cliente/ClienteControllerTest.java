package br.com.snackflow.cliente;

import br.com.snackflow.common.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteControllerTest {

    @Mock
    private ClienteService service;

    @InjectMocks
    private ClienteController controller;

    private final ConcurrentModel model = new ConcurrentModel();
    private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

    private static ClienteForm formValido() {
        ClienteForm form = new ClienteForm();
        form.setNome("Maria");
        return form;
    }

    @Test
    void listaClientesComFormularioVazio() {
        when(service.pesquisar("maria", true)).thenReturn(List.of());

        String view = controller.listar("maria", false, null, model);

        assertThat(view).isEqualTo(ClienteController.VIEW);
        assertThat(model.getAttribute("form")).isInstanceOf(ClienteForm.class);
        assertThat(model.getAttribute("busca")).isEqualTo("maria");
        assertThat(model.getAttribute("inativos")).isEqualTo(false);
        assertThat(model.getAttribute("clientes")).isEqualTo(List.of());
    }

    @Test
    void carregaClienteNoFormularioParaEdicao() {
        Cliente cliente = new Cliente();
        cliente.setId(4);
        cliente.setNome("João");
        cliente.setCpf("52998224725");
        when(service.buscar(4)).thenReturn(cliente);

        controller.listar("", true, 4, model);

        ClienteForm form = (ClienteForm) model.getAttribute("form");
        assertThat(form.getId()).isEqualTo(4);
        assertThat(form.getNome()).isEqualTo("João");
        assertThat(form.getCpf()).isEqualTo("529.982.247-25");
    }

    @Test
    void mostraErroQuandoClienteDaEdicaoNaoExiste() {
        when(service.buscar(9)).thenThrow(new RegraNegocioException("Cliente não encontrado."));

        controller.listar("", false, 9, model);

        assertThat(model.getAttribute("erro")).isEqualTo("Cliente não encontrado.");
    }

    @Test
    void cadastraEVoltaParaALista() {
        ClienteForm form = formValido();
        BindingResult resultado = new BeanPropertyBindingResult(form, "form");

        String view = controller.salvar(form, resultado, model, redirect);

        assertThat(view).isEqualTo("redirect:/clientes");
        assertThat(redirect.getFlashAttributes()).containsEntry("ok", "Cliente cadastrado.");
        verify(service).salvar(form);
    }

    @Test
    void atualizaEVoltaParaALista() {
        ClienteForm form = formValido();
        form.setId(2);
        BindingResult resultado = new BeanPropertyBindingResult(form, "form");

        controller.salvar(form, resultado, model, redirect);

        assertThat(redirect.getFlashAttributes()).containsEntry("ok", "Cliente atualizado.");
    }

    @Test
    void naoSalvaQuandoHaErrosDeValidacao() {
        ClienteForm form = new ClienteForm();
        BindingResult resultado = new BeanPropertyBindingResult(form, "form");
        resultado.addError(new FieldError("form", "nome", "Informe o nome."));

        String view = controller.salvar(form, resultado, model, redirect);

        assertThat(view).isEqualTo(ClienteController.VIEW);
        verify(service, never()).salvar(any());
    }

    @Test
    void mostraMensagemDeRegraDeNegocio() {
        ClienteForm form = formValido();
        BindingResult resultado = new BeanPropertyBindingResult(form, "form");
        doThrow(new RegraNegocioException("CPF inválido.")).when(service).salvar(form);

        String view = controller.salvar(form, resultado, model, redirect);

        assertThat(view).isEqualTo(ClienteController.VIEW);
        assertThat(model.getAttribute("erro")).isEqualTo("CPF inválido.");
    }

    @Test
    void mostraMensagemGenericaQuandoOBancoRecusa() {
        ClienteForm form = formValido();
        BindingResult resultado = new BeanPropertyBindingResult(form, "form");
        doThrow(new DataIntegrityViolationException("falha")).when(service).salvar(form);

        String view = controller.salvar(form, resultado, model, redirect);

        assertThat(view).isEqualTo(ClienteController.VIEW);
        assertThat((String) model.getAttribute("erro")).startsWith("Não foi possível salvar");
    }

    @Test
    void inativaEReativaCliente() {
        assertThat(controller.alterarSituacao(1, false, redirect)).isEqualTo("redirect:/clientes");
        assertThat(redirect.getFlashAttributes()).containsEntry("ok", "Cliente inativado.");

        controller.alterarSituacao(1, true, redirect);
        assertThat(redirect.getFlashAttributes()).containsEntry("ok", "Cliente reativado.");
        verify(service).alterarSituacao(1, false);
        verify(service).alterarSituacao(1, true);
    }

    @Test
    void informaErroAoAlterarSituacaoDeClienteInexistente() {
        doThrow(new RegraNegocioException("Cliente não encontrado.")).when(service).alterarSituacao(8, false);

        controller.alterarSituacao(8, false, redirect);

        assertThat(redirect.getFlashAttributes()).containsEntry("erro", "Cliente não encontrado.");
    }
}
