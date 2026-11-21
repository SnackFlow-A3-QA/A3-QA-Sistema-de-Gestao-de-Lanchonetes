package br.com.snackflow.cliente;

import br.com.snackflow.common.exception.RegraNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository repository;

    public ClienteService(ClienteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Cliente> pesquisar(String busca, boolean somenteAtivos) {
        String termo = busca == null ? "" : busca.trim();
        return repository.pesquisar(termo, Cpf.somenteDigitos(termo), somenteAtivos);
    }

    @Transactional(readOnly = true)
    public Cliente buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado."));
    }

    /** Cadastra (id nulo) ou atualiza um cliente, validando CPF e duplicidade. */
    @Transactional
    public Cliente salvar(ClienteForm form) {
        String cpf = Cpf.somenteDigitos(form.getCpf());
        if (cpf.isEmpty()) {
            cpf = null; // cliente sem CPF
        } else {
            validarCpf(cpf, form.getId());
        }

        Cliente cliente = form.getId() == null ? new Cliente() : buscar(form.getId());
        cliente.setNome(form.getNome().trim());
        cliente.setCpf(cpf);
        cliente.setTelefone(vazioParaNulo(form.getTelefone()));
        cliente.setEmail(vazioParaNulo(form.getEmail()));
        return repository.saveAndFlush(cliente);
    }

    /** Inativa ou reativa o cliente (não há exclusão física: o histórico de pedidos é preservado). */
    @Transactional
    public void alterarSituacao(Integer id, boolean ativo) {
        Cliente cliente = buscar(id);
        cliente.setAtivo(ativo);
        repository.saveAndFlush(cliente);
    }

    private void validarCpf(String cpf, Integer id) {
        if (!Cpf.valido(cpf)) {
            throw new RegraNegocioException("CPF inválido.");
        }
        boolean duplicado = id == null
                ? repository.existsByCpf(cpf)
                : repository.existsByCpfAndIdNot(cpf, id);
        if (duplicado) {
            throw new RegraNegocioException("Já existe um cliente cadastrado com este CPF.");
        }
    }

    private static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
