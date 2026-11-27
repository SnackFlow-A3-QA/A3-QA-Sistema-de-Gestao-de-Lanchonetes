package br.com.snackflow.funcionario;

import br.com.snackflow.common.RegraNegocioException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Regras do cadastro de funcionários (ADMIN): senha só como hash BCrypt, login único, sem espaços e
 * gravado em minúsculas, e o usuário logado não inativa a própria conta.
 *
 * <p>Sem {@code @Transactional}: a verificação de login e a gravação não são atômicas, então a corrida
 * no índice único {@code uk_funcionarios_login} é tratada no {@code catch} da gravação. As mensagens
 * não repetem login nem senha.
 */
@Service
class FuncionarioService {

    static final String MENSAGEM_NAO_ENCONTRADO = "Funcionário não encontrado.";
    static final String MENSAGEM_LOGIN_DUPLICADO = "Já existe um funcionário com este login.";
    static final String MENSAGEM_LOGIN_OBRIGATORIO = "Informe o login.";
    static final String MENSAGEM_LOGIN_COM_ESPACOS = "O login não pode ter espaços.";
    static final String MENSAGEM_SENHA_INVALIDA = "A senha deve ter de 6 a 72 caracteres.";
    static final String MENSAGEM_NOME_OBRIGATORIO = "Informe o nome.";
    static final String MENSAGEM_CARGO_OBRIGATORIO = "Informe o cargo.";
    static final String MENSAGEM_PERFIL_OBRIGATORIO = "Selecione o perfil.";
    static final String MENSAGEM_AUTOINATIVACAO = "Você não pode inativar a sua própria conta.";

    private static final int SENHA_MINIMA = 6;
    private static final int SENHA_MAXIMA = 72;
    private static final String SQLSTATE_INTEGRIDADE = "23000";
    private static final int CODIGO_MYSQL_DUPLICADO = 1062;

    private final FuncionarioRepository repositorio;
    private final PasswordEncoder codificador;

    FuncionarioService(FuncionarioRepository repositorio, PasswordEncoder codificador) {
        this.repositorio = repositorio;
        this.codificador = codificador;
    }

    List<FuncionarioLinha> listar() {
        return repositorio.findAll(Sort.by("nome", "id")).stream()
                .map(f -> new FuncionarioLinha(f.getId(), f.getNome(), f.getCargo(), f.getLogin(), f.getPerfil(),
                        f.isAtivo()))
                .toList();
    }

    FuncionarioForm formularioDe(Integer id) {
        Funcionario funcionario = buscarExistente(id);
        FuncionarioForm form = new FuncionarioForm();
        form.setNome(funcionario.getNome());
        form.setCargo(funcionario.getCargo());
        form.setPerfil(funcionario.getPerfil());
        form.setLogin(funcionario.getLogin());
        return form;
    }

    void cadastrar(NovoFuncionarioForm form) {
        String nome = textoObrigatorio(form.getNome(), MENSAGEM_NOME_OBRIGATORIO);
        String cargo = textoObrigatorio(form.getCargo(), MENSAGEM_CARGO_OBRIGATORIO);
        Perfil perfil = perfilObrigatorio(form.getPerfil());
        String login = loginNormalizado(form.getLogin(), null);
        validarSenha(form.getSenha());

        gravar(new Funcionario(nome, cargo, perfil, login, codificador.encode(form.getSenha())));
    }

    void atualizar(Integer id, FuncionarioForm form) {
        Funcionario funcionario = buscarExistente(id);
        String nome = textoObrigatorio(form.getNome(), MENSAGEM_NOME_OBRIGATORIO);
        String cargo = textoObrigatorio(form.getCargo(), MENSAGEM_CARGO_OBRIGATORIO);
        Perfil perfil = perfilObrigatorio(form.getPerfil());
        String login = loginNormalizado(form.getLogin(), id);

        funcionario.atualizarDados(nome, cargo, perfil, login);
        gravar(funcionario);
    }

    void redefinirSenha(Integer id, String novaSenha) {
        Funcionario funcionario = buscarExistente(id);
        validarSenha(novaSenha);

        funcionario.redefinirSenha(codificador.encode(novaSenha));
        gravar(funcionario);
    }

    /** Ativa ou inativa. O funcionário autenticado não pode inativar a própria conta. */
    void alterarSituacao(Integer id, boolean ativo, UsuarioLogado logado) {
        if (!ativo && logado != null && id != null && id.equals(logado.getId())) {
            throw new RegraNegocioException(MENSAGEM_AUTOINATIVACAO);
        }
        Funcionario funcionario = buscarExistente(id);
        funcionario.setAtivo(ativo);
        gravar(funcionario);
    }

    private Funcionario buscarExistente(Integer id) {
        return repositorio.findById(id).orElseThrow(() -> new RegraNegocioException(MENSAGEM_NAO_ENCONTRADO));
    }

    private static String textoObrigatorio(String valor, String mensagem) {
        String texto = valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
        if (texto.isEmpty()) {
            throw new RegraNegocioException(mensagem);
        }
        return texto;
    }

    private static Perfil perfilObrigatorio(Perfil perfil) {
        if (perfil == null) {
            throw new RegraNegocioException(MENSAGEM_PERFIL_OBRIGATORIO);
        }
        return perfil;
    }

    /** Sem espaços, em minúsculas e único (ignorando o próprio funcionário na edição). */
    private String loginNormalizado(String informado, Integer idDoProprio) {
        if (informado == null || informado.isEmpty()) {
            throw new RegraNegocioException(MENSAGEM_LOGIN_OBRIGATORIO);
        }
        if (informado.chars().anyMatch(Character::isWhitespace)) {
            throw new RegraNegocioException(MENSAGEM_LOGIN_COM_ESPACOS);
        }
        String login = informado.toLowerCase(Locale.ROOT);
        boolean repetido = repositorio.findByLogin(login)
                .filter(outro -> idDoProprio == null || !idDoProprio.equals(outro.getId()))
                .isPresent();
        if (repetido) {
            throw new RegraNegocioException(MENSAGEM_LOGIN_DUPLICADO);
        }
        return login;
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA || senha.length() > SENHA_MAXIMA) {
            throw new RegraNegocioException(MENSAGEM_SENHA_INVALIDA);
        }
    }

    private void gravar(Funcionario funcionario) {
        try {
            repositorio.saveAndFlush(funcionario);
        } catch (DataIntegrityViolationException erro) {
            if (erro instanceof DuplicateKeyException || violouUnicidade(erro)) {
                throw new RegraNegocioException(MENSAGEM_LOGIN_DUPLICADO, erro);
            }
            throw erro;
        }
    }

    private static boolean violouUnicidade(Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            if (atual instanceof SQLException sql && SQLSTATE_INTEGRIDADE.equals(sql.getSQLState())
                    && sql.getErrorCode() == CODIGO_MYSQL_DUPLICADO) {
                return true;
            }
            atual = atual.getCause() == atual ? null : atual.getCause();
        }
        return false;
    }
}
