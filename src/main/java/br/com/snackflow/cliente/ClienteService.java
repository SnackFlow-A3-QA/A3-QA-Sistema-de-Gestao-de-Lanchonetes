package br.com.snackflow.cliente;

import br.com.snackflow.common.RegraNegocioException;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Regras de clientes usadas pelo PDV: busca e cadastro rápido (contrato da seção 14 do guia).
 *
 * <p>Sem {@code @Transactional}: a verificação de duplicidade e a gravação não são atômicas, então
 * a corrida no índice único {@code uk_clientes_cpf} é tratada no {@code catch} da gravação. As
 * mensagens não repetem CPF nem telefone.
 */
@Service
class ClienteService {

    static final String MENSAGEM_CPF_INVALIDO = "CPF inválido.";
    static final String MENSAGEM_CPF_DUPLICADO = "Já existe um cliente cadastrado com este CPF.";
    static final String MENSAGEM_TELEFONE_INVALIDO = "Telefone inválido.";
    static final String MENSAGEM_NOME_OBRIGATORIO = "Informe o nome.";
    static final String MENSAGEM_NAO_ENCONTRADO = "Cliente não encontrado.";

    private static final int LIMITE_BUSCA = 10;
    private static final int MINIMO_BUSCA = 2;
    private static final char ESCAPE = '!';
    private static final String SQLSTATE_INTEGRIDADE = "23000";
    private static final int CODIGO_MYSQL_DUPLICADO = 1062;
    private static final Pattern PARECE_CPF = Pattern.compile("[0-9. \\-]+");

    private final ClienteRepository repositorio;

    ClienteService(ClienteRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Busca parcial por nome ou CPF entre os ativos (até 10). Busca curta ou ausente: lista vazia. */
    List<ClienteResposta> buscar(String q) {
        if (q == null) {
            return List.of();
        }
        String texto = q.trim();
        List<Cliente> achados;
        if (!texto.isEmpty() && PARECE_CPF.matcher(texto).matches()) {
            String digitos = Cpf.somenteDigitos(texto);
            if (digitos.length() < MINIMO_BUSCA) {
                return List.of();
            }
            achados = repositorio.buscarPorCpf("%" + escaparLike(digitos) + "%", PageRequest.of(0, LIMITE_BUSCA));
        } else {
            if (texto.length() < MINIMO_BUSCA) {
                return List.of();
            }
            achados = repositorio.buscarPorNome("%" + escaparLike(texto) + "%", PageRequest.of(0, LIMITE_BUSCA));
        }
        return achados.stream().map(ClienteResposta::de).toList();
    }

    ClienteResposta cadastrarRapido(ClienteRapidoForm form) {
        String nome = nomeNormalizado(form);
        String telefone = telefoneFormatado(form);
        String cpf = normalizarCpf(form.cpf(), null);
        return ClienteResposta.de(gravar(new Cliente(nome, cpf, telefone)));
    }

    /** Edição: CPF continua único, ignorando o próprio cliente. Cliente inexistente vira 422. */
    ClienteResposta atualizar(Integer id, ClienteRapidoForm form) {
        Cliente cliente = buscarExistente(id);
        String nome = nomeNormalizado(form);
        String telefone = telefoneFormatado(form);
        String cpf = normalizarCpf(form.cpf(), id);
        cliente.atualizar(nome, cpf, telefone);
        return ClienteResposta.de(gravar(cliente));
    }

    /** Inativação lógica (sem exclusão física). Repetir a chamada não é erro. */
    void inativar(Integer id) {
        Cliente cliente = buscarExistente(id);
        cliente.inativar();
        gravar(cliente);
    }

    private Cliente buscarExistente(Integer id) {
        return repositorio.findById(id).orElseThrow(() -> new RegraNegocioException(MENSAGEM_NAO_ENCONTRADO));
    }

    private String nomeNormalizado(ClienteRapidoForm form) {
        String nome = form.nome() == null ? "" : form.nome().trim().replaceAll("\\s+", " ");
        if (nome.isEmpty()) {
            throw new RegraNegocioException(MENSAGEM_NOME_OBRIGATORIO);
        }
        return nome;
    }

    private String telefoneFormatado(ClienteRapidoForm form) {
        return Telefone.formatar(form.telefone())
                .orElseThrow(() -> new RegraNegocioException(MENSAGEM_TELEFONE_INVALIDO));
    }

    private Cliente gravar(Cliente cliente) {
        try {
            return repositorio.saveAndFlush(cliente);
        } catch (DataIntegrityViolationException erro) {
            if (erro instanceof DuplicateKeyException || violouUnicidade(erro)) {
                throw new RegraNegocioException(MENSAGEM_CPF_DUPLICADO, erro);
            }
            throw erro;
        }
    }

    /** Vazio vira nulo; inválido ou repetido vira RegraNegocioException (422). */
    private String normalizarCpf(String informado, Integer idDoProprio) {
        if (informado == null || informado.isBlank()) {
            return null;
        }
        String digitos = Cpf.somenteDigitos(informado);
        if (!Cpf.valido(digitos)) {
            throw new RegraNegocioException(MENSAGEM_CPF_INVALIDO);
        }
        boolean repetido = idDoProprio == null
                ? repositorio.existsByCpf(digitos)
                : repositorio.existsByCpfAndIdNot(digitos, idDoProprio);
        if (repetido) {
            throw new RegraNegocioException(MENSAGEM_CPF_DUPLICADO);
        }
        return digitos;
    }

    /** Escapa o caractere de escape primeiro, depois os curingas do LIKE. */
    static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
