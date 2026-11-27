package br.com.snackflow.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.snackflow.common.RegraNegocioException;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    private static final String CPF_VALIDO = "39053344705";

    @Mock
    private ClienteRepository repositorio;

    @InjectMocks
    private ClienteService servico;

    private Cliente cliente(int id, String nome, String cpf, String telefone) {
        Cliente cliente = new Cliente(nome, cpf, telefone);
        ReflectionTestUtils.setField(cliente, "id", id);
        return cliente;
    }

    private void gravarComoId7() {
        when(repositorio.saveAndFlush(any(Cliente.class))).thenAnswer(chamada -> {
            Cliente gravado = chamada.getArgument(0);
            ReflectionTestUtils.setField(gravado, "id", 7);
            return gravado;
        });
    }

    // ---------- busca ----------

    @Test
    void deveResponderListaVaziaSemConsultarEmBuscaAusenteOuCurta() {
        assertThat(servico.buscar(null)).isEmpty();
        assertThat(servico.buscar("")).isEmpty();
        assertThat(servico.buscar("  a ")).isEmpty();
        assertThat(servico.buscar("7")).isEmpty();
        assertThat(servico.buscar(" . - ")).isEmpty();

        verifyNoInteractions(repositorio);
    }

    @Test
    void deveBuscarPorNomeComLimiteDe10() {
        when(repositorio.buscarPorNome(eq("%Ana%"), any(Pageable.class)))
                .thenReturn(List.of(cliente(1, "Ana Lima", null, null)));

        List<ClienteResposta> resultado = servico.buscar("  Ana ");

        assertThat(resultado).containsExactly(new ClienteResposta(1, "Ana Lima", null, null));
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).buscarPorNome(eq("%Ana%"), pagina.capture());
        assertThat(pagina.getValue().getPageSize()).isEqualTo(10);
        assertThat(pagina.getValue().getPageNumber()).isZero();
    }

    @Test
    void deveEscaparCuringasDoLikeNaBuscaPorNome() {
        when(repositorio.buscarPorNome(eq("%a!%b!_c!!%"), any(Pageable.class))).thenReturn(List.of());

        assertThat(servico.buscar("a%b_c!")).isEmpty();

        verify(repositorio).buscarPorNome(eq("%a!%b!_c!!%"), any(Pageable.class));
    }

    @Test
    void deveBuscarPorCpfRemovendoPontuacao() {
        when(repositorio.buscarPorCpf(eq("%390533%"), any(Pageable.class)))
                .thenReturn(List.of(cliente(2, "Mariana", CPF_VALIDO, "(48) 98765-4321")));

        List<ClienteResposta> resultado = servico.buscar("390.533");

        assertThat(resultado).containsExactly(new ClienteResposta(2, "Mariana", CPF_VALIDO, "(48) 98765-4321"));
        verify(repositorio, never()).buscarPorNome(any(), any());
    }

    // ---------- cadastro rápido ----------

    @Test
    void deveCadastrarNormalizandoNomeTelefoneECpf() {
        when(repositorio.existsByCpf(CPF_VALIDO)).thenReturn(false);
        gravarComoId7();

        ClienteResposta resposta = servico.cadastrarRapido(
                new ClienteRapidoForm("  Mariana   Costa ", "48987654321", "390.533.447-05"));

        assertThat(resposta).isEqualTo(new ClienteResposta(7, "Mariana Costa", CPF_VALIDO, "(48) 98765-4321"));
        ArgumentCaptor<Cliente> gravado = ArgumentCaptor.forClass(Cliente.class);
        verify(repositorio).saveAndFlush(gravado.capture());
        assertThat(gravado.getValue().getNome()).isEqualTo("Mariana Costa");
        assertThat(gravado.getValue().getCpf()).isEqualTo(CPF_VALIDO);
        assertThat(gravado.getValue().isAtivo()).isTrue();
    }

    @Test
    void deveCadastrarSemCpfSemConsultarDuplicidade() {
        gravarComoId7();

        ClienteResposta resposta = servico.cadastrarRapido(new ClienteRapidoForm("José", "(11) 3456-7890", "  "));

        assertThat(resposta.cpf()).isNull();
        assertThat(resposta.telefone()).isEqualTo("(11) 3456-7890");
        verify(repositorio, never()).existsByCpf(any());
    }

    @Test
    void deveRecusarCpfInvalidoSemGravar() {
        assertThatThrownBy(() -> servico.cadastrarRapido(new ClienteRapidoForm("Ana", "48987654321", "111.111.111-11")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("CPF inválido.");

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarCpfDuplicadoVerificadoAntesDeGravarSemRepetirOCpf() {
        when(repositorio.existsByCpf(CPF_VALIDO)).thenReturn(true);

        assertThatThrownBy(() -> servico.cadastrarRapido(new ClienteRapidoForm("Ana", "48987654321", CPF_VALIDO)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(ClienteService.MENSAGEM_CPF_DUPLICADO)
                .message().doesNotContain(CPF_VALIDO);

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveConverterDuplicateKeyExceptionDaCorridaEmRegraDeNegocio() {
        when(repositorio.existsByCpf(CPF_VALIDO)).thenReturn(false);
        DuplicateKeyException corrida = new DuplicateKeyException("Duplicate entry 'x' for key 'uk_clientes_cpf'");
        when(repositorio.saveAndFlush(any(Cliente.class))).thenThrow(corrida);

        assertThatThrownBy(() -> servico.cadastrarRapido(new ClienteRapidoForm("Ana", "48987654321", CPF_VALIDO)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(ClienteService.MENSAGEM_CPF_DUPLICADO)
                .hasCause(corrida);
    }

    @Test
    void deveConverterViolacaoDeUnicidadeDoJpaEmRegraDeNegocio() {
        when(repositorio.existsByCpf(CPF_VALIDO)).thenReturn(false);
        DataIntegrityViolationException corrida = new DataIntegrityViolationException("falha",
                new SQLException("Duplicate entry 'x' for key 'uk_clientes_cpf'", "23000", 1062));
        when(repositorio.saveAndFlush(any(Cliente.class))).thenThrow(corrida);

        assertThatThrownBy(() -> servico.cadastrarRapido(new ClienteRapidoForm("Ana", "48987654321", CPF_VALIDO)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(ClienteService.MENSAGEM_CPF_DUPLICADO)
                .hasCause(corrida);
    }

    @Test
    void deveRepassarOutroErroDeIntegridadeSemConverterEmDuplicidade() {
        DataIntegrityViolationException outro = new DataIntegrityViolationException("falha",
                new SQLException("Estoque insuficiente", "45000"));
        when(repositorio.saveAndFlush(any(Cliente.class))).thenThrow(outro);

        assertThatThrownBy(() -> servico.cadastrarRapido(new ClienteRapidoForm("Ana", "48987654321", null)))
                .isSameAs(outro);
    }

    @Test
    void deveRecusarTelefoneInvalidoSemGravar() {
        assertThatThrownBy(() -> servico.cadastrarRapido(new ClienteRapidoForm("Ana", "123", null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Telefone inválido.");

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarNomeEmBranco() {
        assertThatThrownBy(() -> servico.cadastrarRapido(new ClienteRapidoForm("   ", "48987654321", null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Informe o nome.");

        assertThatThrownBy(() -> servico.cadastrarRapido(new ClienteRapidoForm(null, "48987654321", null)))
                .isInstanceOf(RegraNegocioException.class);
    }
}
