package br.com.snackflow.cliente;

import br.com.snackflow.common.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository repository;

    @InjectMocks
    private ClienteService service;

    private static ClienteForm form(Integer id, String nome, String cpf) {
        ClienteForm form = new ClienteForm();
        form.setId(id);
        form.setNome(nome);
        form.setCpf(cpf);
        return form;
    }

    @Test
    void pesquisarEnviaTextoEDigitosParaORepositorio() {
        Cliente maria = new Cliente();
        when(repository.pesquisar("529.982", "529982", true)).thenReturn(List.of(maria));

        assertThat(service.pesquisar("  529.982 ", true)).containsExactly(maria);
    }

    @Test
    void pesquisarSemTextoListaTodos() {
        when(repository.pesquisar("", "", false)).thenReturn(List.of());

        assertThat(service.pesquisar(null, false)).isEmpty();
    }

    @Test
    void cadastraClienteNormalizandoOsDados() {
        when(repository.existsByCpf("52998224725")).thenReturn(false);
        when(repository.saveAndFlush(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        ClienteForm form = form(null, "  Maria Silva ", "529.982.247-25");
        form.setTelefone("   ");
        form.setEmail(" maria@teste.com ");

        service.salvar(form);

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(repository).saveAndFlush(captor.capture());
        Cliente salvo = captor.getValue();
        assertThat(salvo.getNome()).isEqualTo("Maria Silva");
        assertThat(salvo.getCpf()).isEqualTo("52998224725");
        assertThat(salvo.getTelefone()).isNull();
        assertThat(salvo.getEmail()).isEqualTo("maria@teste.com");
        assertThat(salvo.isAtivo()).isTrue();
    }

    @Test
    void cadastraClienteSemCpf() {
        when(repository.saveAndFlush(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        Cliente salvo = service.salvar(form(null, "Cliente de balcão", ""));

        assertThat(salvo.getCpf()).isNull();
        verify(repository, never()).existsByCpf(any());
    }

    @Test
    void rejeitaCpfInvalido() {
        assertThatThrownBy(() -> service.salvar(form(null, "Maria", "111.111.111-11")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("CPF inválido.");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejeitaCpfDuplicadoNoCadastro() {
        when(repository.existsByCpf("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> service.salvar(form(null, "Maria", "52998224725")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF");
    }

    @Test
    void rejeitaCpfDuplicadoNaEdicao() {
        when(repository.existsByCpfAndIdNot("52998224725", 7)).thenReturn(true);

        assertThatThrownBy(() -> service.salvar(form(7, "Maria", "52998224725")))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void atualizaClienteExistente() {
        Cliente existente = new Cliente();
        existente.setId(7);
        existente.setNome("Nome antigo");
        when(repository.existsByCpfAndIdNot("52998224725", 7)).thenReturn(false);
        when(repository.findById(7)).thenReturn(Optional.of(existente));
        when(repository.saveAndFlush(existente)).thenReturn(existente);

        Cliente atualizado = service.salvar(form(7, "Nome novo", "52998224725"));

        assertThat(atualizado.getNome()).isEqualTo("Nome novo");
    }

    @Test
    void naoAtualizaClienteInexistente() {
        when(repository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.salvar(form(99, "Maria", "")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Cliente não encontrado.");
    }

    @Test
    void alteraSituacaoDoCliente() {
        Cliente cliente = new Cliente();
        when(repository.findById(3)).thenReturn(Optional.of(cliente));

        service.alterarSituacao(3, false);

        assertThat(cliente.isAtivo()).isFalse();
        verify(repository).saveAndFlush(cliente);
    }
}
