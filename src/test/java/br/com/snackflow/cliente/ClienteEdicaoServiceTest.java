package br.com.snackflow.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.snackflow.common.RegraNegocioException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClienteEdicaoServiceTest {

    private static final String CPF_VALIDO = "39053344705";

    @Mock
    private ClienteRepository repositorio;

    @InjectMocks
    private ClienteService servico;

    private Cliente existente() {
        Cliente cliente = new Cliente("Antigo", null, "(11) 3456-7890");
        ReflectionTestUtils.setField(cliente, "id", 5);
        when(repositorio.findById(5)).thenReturn(Optional.of(cliente));
        return cliente;
    }

    private void gravarDevolvendoOArgumento() {
        when(repositorio.saveAndFlush(any(Cliente.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void deveAtualizarNormalizandoEIgnorandoOProprioNaDuplicidade() {
        Cliente cliente = existente();
        when(repositorio.existsByCpfAndIdNot(CPF_VALIDO, 5)).thenReturn(false);
        gravarDevolvendoOArgumento();

        ClienteResposta resposta = servico.atualizar(5,
                new ClienteRapidoForm("  Novo   Nome ", "48987654321", "390.533.447-05"));

        assertThat(resposta).isEqualTo(new ClienteResposta(5, "Novo Nome", CPF_VALIDO, "(48) 98765-4321"));
        assertThat(cliente.getNome()).isEqualTo("Novo Nome");
        verify(repositorio, never()).existsByCpf(any());
    }

    @Test
    void devePermitirRemoverOCpfNaEdicao() {
        existente();
        gravarDevolvendoOArgumento();

        ClienteResposta resposta = servico.atualizar(5, new ClienteRapidoForm("Antigo", "(11) 3456-7890", ""));

        assertThat(resposta.cpf()).isNull();
        verify(repositorio, never()).existsByCpfAndIdNot(any(), any());
    }

    @Test
    void deveRecusarCpfDeOutroClienteSemGravar() {
        existente();
        when(repositorio.existsByCpfAndIdNot(CPF_VALIDO, 5)).thenReturn(true);

        assertThatThrownBy(() -> servico.atualizar(5, new ClienteRapidoForm("Ana", "48987654321", CPF_VALIDO)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(ClienteService.MENSAGEM_CPF_DUPLICADO)
                .message().doesNotContain(CPF_VALIDO);

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarCpfInvalidoTelefoneInvalidoENomeEmBranco() {
        existente();

        assertThatThrownBy(() -> servico.atualizar(5, new ClienteRapidoForm("Ana", "48987654321", "111.111.111-11")))
                .hasMessage("CPF inválido.");
        assertThatThrownBy(() -> servico.atualizar(5, new ClienteRapidoForm("Ana", "123", null)))
                .hasMessage("Telefone inválido.");
        assertThatThrownBy(() -> servico.atualizar(5, new ClienteRapidoForm("  ", "48987654321", null)))
                .hasMessage("Informe o nome.");

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarClienteInexistenteNaEdicao() {
        when(repositorio.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.atualizar(99, new ClienteRapidoForm("Ana", "48987654321", null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Cliente não encontrado.");

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveConverterCorridaDeIndiceUnicoNaEdicaoEmRegraDeNegocio() {
        existente();
        when(repositorio.existsByCpfAndIdNot(CPF_VALIDO, 5)).thenReturn(false);
        DuplicateKeyException corrida = new DuplicateKeyException("Duplicate entry");
        when(repositorio.saveAndFlush(any(Cliente.class))).thenThrow(corrida);

        assertThatThrownBy(() -> servico.atualizar(5, new ClienteRapidoForm("Ana", "48987654321", CPF_VALIDO)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(ClienteService.MENSAGEM_CPF_DUPLICADO)
                .hasCause(corrida);
    }

    @Test
    void deveInativarSemExcluir() {
        Cliente cliente = existente();
        gravarDevolvendoOArgumento();

        servico.inativar(5);

        assertThat(cliente.isAtivo()).isFalse();
        verify(repositorio).saveAndFlush(cliente);
        verify(repositorio, never()).delete(any(Cliente.class));
        verify(repositorio, never()).deleteById(any());
    }

    @Test
    void deveRecusarInativarClienteInexistente() {
        when(repositorio.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.inativar(99))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Cliente não encontrado.");

        verify(repositorio, never()).saveAndFlush(any());
    }
}
