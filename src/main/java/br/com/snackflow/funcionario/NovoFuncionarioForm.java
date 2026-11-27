package br.com.snackflow.funcionario;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro: os dados do funcionário mais a senha inicial (6 a 72 caracteres). */
public class NovoFuncionarioForm extends FuncionarioForm {

    @NotNull(message = "Informe a senha.")
    @Size(min = 6, max = 72, message = "A senha deve ter de 6 a 72 caracteres.")
    private String senha;

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
}
