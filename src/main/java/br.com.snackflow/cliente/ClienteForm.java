package br.com.snackflow.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dados do formulário de cliente (evita expor a entidade inteira ao binding). */
public class ClienteForm {

    private Integer id;

    @NotBlank(message = "Informe o nome.")
    @Size(max = 100, message = "O nome pode ter no máximo 100 caracteres.")
    private String nome;

    @Size(max = 14, message = "CPF inválido.")
    private String cpf;

    @Size(max = 20, message = "O telefone pode ter no máximo 20 caracteres.")
    private String telefone;

    @Email(message = "E-mail inválido.")
    @Size(max = 100, message = "O e-mail pode ter no máximo 100 caracteres.")
    private String email;

    public static ClienteForm de(Cliente cliente) {
        ClienteForm form = new ClienteForm();
        form.setId(cliente.getId());
        form.setNome(cliente.getNome());
        form.setCpf(cliente.getCpfFormatado());
        form.setTelefone(cliente.getTelefone());
        form.setEmail(cliente.getEmail());
        return form;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
