package br.com.snackflow.cliente;

/** Item devolvido pela busca e pelo cadastro rápido. {@code cpf}: só dígitos ou nulo. */
public record ClienteResposta(Integer id, String nome, String cpf, String telefone) {

    static ClienteResposta de(Cliente cliente) {
        return new ClienteResposta(cliente.getId(), cliente.getNome(), cliente.getCpf(), cliente.getTelefone());
    }
}
