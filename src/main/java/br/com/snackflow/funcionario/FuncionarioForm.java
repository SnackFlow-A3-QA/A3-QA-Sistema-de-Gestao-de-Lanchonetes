package br.com.snackflow.funcionario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados cadastrais do funcionário (edição). Classe com getters e setters, como exige o th:field. */
public class FuncionarioForm {

    @NotBlank(message = "Informe o nome.")
    @Size(max = 100, message = "Nome muito longo.")
    private String nome;

    @NotBlank(message = "Informe o cargo.")
    @Size(max = 50, message = "Cargo muito longo.")
    private String cargo;

    @NotNull(message = "Selecione o perfil.")
    private Perfil perfil;

    @NotEmpty(message = "Informe o login.")
    @Size(max = 50, message = "Login muito longo.")
    @Pattern(regexp = "\\S*", message = "O login não pode ter espaços.")
    private String login;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public void setPerfil(Perfil perfil) {
        this.perfil = perfil;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }
}
