package br.com.snackflow.funcionario;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FuncionarioFormTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private NovoFuncionarioForm form(String nome, String cargo, Perfil perfil, String login, String senha) {
        NovoFuncionarioForm form = new NovoFuncionarioForm();
        form.setNome(nome);
        form.setCargo(cargo);
        form.setPerfil(perfil);
        form.setLogin(login);
        form.setSenha(senha);
        return form;
    }

    private Set<String> mensagens(Object form) {
        return validador.validate(form).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void deveAceitarFormularioValido() {
        assertThat(mensagens(form("Fernanda", "Atendente", Perfil.CAIXA, "fernanda.caixa", "segredo1"))).isEmpty();
    }

    @Test
    void deveExigirTodosOsCampos() {
        assertThat(mensagens(form(" ", "", null, "", null)))
                .containsExactlyInAnyOrder("Informe o nome.", "Informe o cargo.", "Selecione o perfil.",
                        "Informe o login.", "Informe a senha.");
    }

    @Test
    void deveRecusarLoginComEspacosComUmaSoMensagem() {
        assertThat(mensagens(form("F", "C", Perfil.CAIXA, "fernanda caixa", "segredo1")))
                .containsExactly("O login não pode ter espaços.");
        assertThat(mensagens(form("F", "C", Perfil.CAIXA, " ", "segredo1")))
                .containsExactly("O login não pode ter espaços.");
    }

    @Test
    void deveExigirSenhaDe6A72Caracteres() {
        assertThat(mensagens(form("F", "C", Perfil.CAIXA, "f", "12345")))
                .containsExactly("A senha deve ter de 6 a 72 caracteres.");
        assertThat(mensagens(form("F", "C", Perfil.CAIXA, "f", "x".repeat(73))))
                .containsExactly("A senha deve ter de 6 a 72 caracteres.");
        assertThat(mensagens(form("F", "C", Perfil.CAIXA, "f", "123456"))).isEmpty();
    }

    @Test
    void deveValidarSenhaDaRedefinicao() {
        SenhaForm curta = new SenhaForm();
        curta.setSenha("abc");
        SenhaForm boa = new SenhaForm();
        boa.setSenha("abcdef");

        assertThat(mensagens(curta)).containsExactly("A senha deve ter de 6 a 72 caracteres.");
        assertThat(mensagens(boa)).isEmpty();
        assertThat(mensagens(new SenhaForm())).containsExactly("Informe a senha.");
    }

    @Test
    void naoDeveExigirSenhaNaEdicao() {
        FuncionarioForm edicao = new FuncionarioForm();
        edicao.setNome("F");
        edicao.setCargo("C");
        edicao.setPerfil(Perfil.ADMIN);
        edicao.setLogin("f");

        assertThat(mensagens(edicao)).isEmpty();
    }
}
