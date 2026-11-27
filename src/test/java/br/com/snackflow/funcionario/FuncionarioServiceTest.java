package br.com.snackflow.funcionario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.snackflow.common.RegraNegocioException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FuncionarioServiceTest {

    @Mock
    private FuncionarioRepository repositorio;
    @Mock
    private PasswordEncoder codificador;

    @InjectMocks
    private FuncionarioService servico;

    private Funcionario funcionario(int id, String login) {
        Funcionario funcionario = new Funcionario("Antigo", "Cargo", Perfil.CAIXA, login, "hash-antigo");
        ReflectionTestUtils.setField(funcionario, "id", id);
        return funcionario;
    }

    private NovoFuncionarioForm novo(String nome, String cargo, Perfil perfil, String login, String senha) {
        NovoFuncionarioForm form = new NovoFuncionarioForm();
        form.setNome(nome);
        form.setCargo(cargo);
        form.setPerfil(perfil);
        form.setLogin(login);
        form.setSenha(senha);
        return form;
    }

    private FuncionarioForm edicao(String nome, String cargo, Perfil perfil, String login) {
        FuncionarioForm form = new FuncionarioForm();
        form.setNome(nome);
        form.setCargo(cargo);
        form.setPerfil(perfil);
        form.setLogin(login);
        return form;
    }

    private UsuarioLogado logado(int id) {
        return new UsuarioLogado(id, "Eu", "eu", "hash", Perfil.ADMIN, true);
    }

    // ---------- listar e formulário ----------

    @Test
    void deveListarSemExporOHash() {
        when(repositorio.findAll(any(Sort.class))).thenReturn(List.of(funcionario(3, "marcos")));

        List<FuncionarioLinha> linhas = servico.listar();

        assertThat(linhas).containsExactly(
                new FuncionarioLinha(3, "Antigo", "Cargo", "marcos", Perfil.CAIXA, true));
    }

    @Test
    void deveDevolverListaVaziaQuandoNaoHaFuncionarios() {
        when(repositorio.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(servico.listar()).isEmpty();
    }

    @Test
    void deveMontarFormularioDeEdicaoSemSenha() {
        when(repositorio.findById(3)).thenReturn(Optional.of(funcionario(3, "marcos")));

        FuncionarioForm form = servico.formularioDe(3);

        assertThat(form.getNome()).isEqualTo("Antigo");
        assertThat(form.getCargo()).isEqualTo("Cargo");
        assertThat(form.getPerfil()).isEqualTo(Perfil.CAIXA);
        assertThat(form.getLogin()).isEqualTo("marcos");
    }

    @Test
    void deveRecusarFormularioDeFuncionarioInexistente() {
        when(repositorio.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.formularioDe(99))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Funcionário não encontrado.");
    }

    // ---------- cadastrar ----------

    @Test
    void deveCadastrarComLoginEmMinusculasENomeNormalizadoEHashDaSenha() {
        when(repositorio.findByLogin("fernanda.caixa")).thenReturn(Optional.empty());
        when(codificador.encode("segredo1")).thenReturn("hash-novo");
        when(repositorio.saveAndFlush(any(Funcionario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        servico.cadastrar(novo("  Fernanda   Lima ", " Atendente ", Perfil.CAIXA, "Fernanda.Caixa", "segredo1"));

        ArgumentCaptor<Funcionario> gravado = ArgumentCaptor.forClass(Funcionario.class);
        verify(repositorio).saveAndFlush(gravado.capture());
        Funcionario f = gravado.getValue();
        assertThat(f.getNome()).isEqualTo("Fernanda Lima");
        assertThat(f.getCargo()).isEqualTo("Atendente");
        assertThat(f.getPerfil()).isEqualTo(Perfil.CAIXA);
        assertThat(f.getLogin()).isEqualTo("fernanda.caixa");
        assertThat(f.getSenhaHash()).isEqualTo("hash-novo").isNotEqualTo("segredo1");
        assertThat(f.isAtivo()).isTrue();
    }

    @Test
    void deveRecusarLoginDuplicadoSemGravarNemCodificar() {
        when(repositorio.findByLogin("ana")).thenReturn(Optional.of(funcionario(2, "ana")));

        assertThatThrownBy(() -> servico.cadastrar(novo("Ana", "Caixa", Perfil.CAIXA, "ANA", "segredo1")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(FuncionarioService.MENSAGEM_LOGIN_DUPLICADO)
                .message().doesNotContain("ana");

        verify(repositorio, never()).saveAndFlush(any());
        verifyNoInteractions(codificador);
    }

    @Test
    void deveRecusarLoginComEspacosOuVazio() {
        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, "ana paula", "segredo1")))
                .hasMessage("O login não pode ter espaços.");
        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, " ana", "segredo1")))
                .hasMessage("O login não pode ter espaços.");
        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, "", "segredo1")))
                .hasMessage("Informe o login.");
        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, null, "segredo1")))
                .hasMessage("Informe o login.");

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarSenhaForaDoLimite() {
        when(repositorio.findByLogin("ana")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, "ana", "12345")))
                .hasMessage("A senha deve ter de 6 a 72 caracteres.");
        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, "ana", "x".repeat(73))))
                .hasMessage("A senha deve ter de 6 a 72 caracteres.");
        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, "ana", null)))
                .hasMessage("A senha deve ter de 6 a 72 caracteres.");

        verifyNoInteractions(codificador);
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarNomeCargoEPerfilAusentes() {
        assertThatThrownBy(() -> servico.cadastrar(novo("  ", "C", Perfil.CAIXA, "ana", "segredo1")))
                .hasMessage("Informe o nome.");
        assertThatThrownBy(() -> servico.cadastrar(novo("A", " ", Perfil.CAIXA, "ana", "segredo1")))
                .hasMessage("Informe o cargo.");
        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", null, "ana", "segredo1")))
                .hasMessage("Selecione o perfil.");
    }

    @Test
    void deveConverterCorridaDoIndiceUnicoEmRegraDeNegocio() {
        when(repositorio.findByLogin("ana")).thenReturn(Optional.empty());
        when(codificador.encode("segredo1")).thenReturn("hash");
        DuplicateKeyException corrida = new DuplicateKeyException("Duplicate entry 'ana' for key 'uk_funcionarios_login'");
        when(repositorio.saveAndFlush(any(Funcionario.class))).thenThrow(corrida);

        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, "ana", "segredo1")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(FuncionarioService.MENSAGEM_LOGIN_DUPLICADO)
                .hasCause(corrida);
    }

    @Test
    void deveConverterViolacaoDeUnicidadeDoJpaEmRegraDeNegocio() {
        when(repositorio.findByLogin("ana")).thenReturn(Optional.empty());
        when(codificador.encode("segredo1")).thenReturn("hash");
        DataIntegrityViolationException corrida = new DataIntegrityViolationException("falha",
                new SQLException("Duplicate entry", "23000", 1062));
        when(repositorio.saveAndFlush(any(Funcionario.class))).thenThrow(corrida);

        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, "ana", "segredo1")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(FuncionarioService.MENSAGEM_LOGIN_DUPLICADO)
                .hasCause(corrida);
    }

    @Test
    void deveRepassarOutroErroDeIntegridade() {
        when(repositorio.findByLogin("ana")).thenReturn(Optional.empty());
        when(codificador.encode("segredo1")).thenReturn("hash");
        DataIntegrityViolationException outro = new DataIntegrityViolationException("falha",
                new SQLException("Data too long", "22001", 1406));
        when(repositorio.saveAndFlush(any(Funcionario.class))).thenThrow(outro);

        assertThatThrownBy(() -> servico.cadastrar(novo("A", "C", Perfil.CAIXA, "ana", "segredo1")))
                .isSameAs(outro);
    }

    // ---------- atualizar ----------

    @Test
    void deveAtualizarIgnorandoOProprioLoginNaDuplicidade() {
        Funcionario existente = funcionario(5, "ana");
        when(repositorio.findById(5)).thenReturn(Optional.of(existente));
        when(repositorio.findByLogin("ana")).thenReturn(Optional.of(existente));
        when(repositorio.saveAndFlush(any(Funcionario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        servico.atualizar(5, edicao(" Ana  Paula ", "Gerente", Perfil.ADMIN, "Ana"));

        assertThat(existente.getNome()).isEqualTo("Ana Paula");
        assertThat(existente.getCargo()).isEqualTo("Gerente");
        assertThat(existente.getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(existente.getLogin()).isEqualTo("ana");
        assertThat(existente.getSenhaHash()).isEqualTo("hash-antigo");
        verifyNoInteractions(codificador);
    }

    @Test
    void deveRecusarLoginDeOutroFuncionarioNaEdicao() {
        when(repositorio.findById(5)).thenReturn(Optional.of(funcionario(5, "ana")));
        when(repositorio.findByLogin("marcos")).thenReturn(Optional.of(funcionario(3, "marcos")));

        assertThatThrownBy(() -> servico.atualizar(5, edicao("Ana", "Caixa", Perfil.CAIXA, "marcos")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(FuncionarioService.MENSAGEM_LOGIN_DUPLICADO);

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarEdicaoDeFuncionarioInexistente() {
        when(repositorio.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.atualizar(99, edicao("A", "C", Perfil.CAIXA, "a")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Funcionário não encontrado.");
    }

    @Test
    void deveRecusarDadosInvalidosNaEdicao() {
        when(repositorio.findById(5)).thenReturn(Optional.of(funcionario(5, "ana")));

        assertThatThrownBy(() -> servico.atualizar(5, edicao(" ", "C", Perfil.CAIXA, "ana")))
                .hasMessage("Informe o nome.");
        assertThatThrownBy(() -> servico.atualizar(5, edicao("A", "C", Perfil.CAIXA, "ana paula")))
                .hasMessage("O login não pode ter espaços.");

        verify(repositorio, never()).saveAndFlush(any());
    }

    // ---------- redefinir senha ----------

    @Test
    void deveRedefinirSenhaGravandoSoOHash() {
        Funcionario existente = funcionario(5, "ana");
        when(repositorio.findById(5)).thenReturn(Optional.of(existente));
        when(codificador.encode("nova-senha")).thenReturn("hash-nova");
        when(repositorio.saveAndFlush(any(Funcionario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        servico.redefinirSenha(5, "nova-senha");

        assertThat(existente.getSenhaHash()).isEqualTo("hash-nova");
    }

    @Test
    void deveRecusarSenhaCurtaNaRedefinicao() {
        when(repositorio.findById(5)).thenReturn(Optional.of(funcionario(5, "ana")));

        assertThatThrownBy(() -> servico.redefinirSenha(5, "12345"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A senha deve ter de 6 a 72 caracteres.");

        verifyNoInteractions(codificador);
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarRedefinicaoDeFuncionarioInexistente() {
        when(repositorio.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.redefinirSenha(99, "nova-senha"))
                .hasMessage("Funcionário não encontrado.");
    }

    // ---------- ativar e inativar ----------

    @Test
    void deveInativarOutroFuncionario() {
        Funcionario existente = funcionario(5, "ana");
        when(repositorio.findById(5)).thenReturn(Optional.of(existente));
        when(repositorio.saveAndFlush(any(Funcionario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        servico.alterarSituacao(5, false, logado(1));

        assertThat(existente.isAtivo()).isFalse();
        verify(repositorio).saveAndFlush(existente);
    }

    @Test
    void deveReativarFuncionario() {
        Funcionario existente = funcionario(5, "ana");
        existente.setAtivo(false);
        when(repositorio.findById(5)).thenReturn(Optional.of(existente));
        when(repositorio.saveAndFlush(any(Funcionario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        servico.alterarSituacao(5, true, logado(1));

        assertThat(existente.isAtivo()).isTrue();
    }

    @Test
    void deveRecusarInativarAPropriaConta() {
        assertThatThrownBy(() -> servico.alterarSituacao(1, false, logado(1)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Você não pode inativar a sua própria conta.");

        verifyNoInteractions(repositorio);
    }

    @Test
    void deveRecusarAlterarSituacaoDeFuncionarioInexistente() {
        when(repositorio.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.alterarSituacao(99, false, logado(1)))
                .hasMessage("Funcionário não encontrado.");
    }
}
