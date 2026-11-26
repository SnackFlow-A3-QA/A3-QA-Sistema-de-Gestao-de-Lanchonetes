package br.com.snackflow.funcionario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class FuncionarioUserDetailsServiceTest {

    @Mock
    private FuncionarioRepository repositorio;

    @InjectMocks
    private FuncionarioUserDetailsService servico;

    @Test
    void deveEntregarAutoridadeDoPerfilEHashDaSenha() {
        Funcionario funcionario = new Funcionario("Ana", "Caixa", Perfil.CAIXA, "ana.caixa", "hash-bcrypt");
        when(repositorio.findByLogin("ana.caixa")).thenReturn(Optional.of(funcionario));

        UserDetails usuario = servico.loadUserByUsername("ana.caixa");

        assertThat(usuario.getUsername()).isEqualTo("ana.caixa");
        assertThat(usuario.getPassword()).isEqualTo("hash-bcrypt");
        assertThat(usuario.getAuthorities()).extracting("authority").containsExactly("ROLE_CAIXA");
    }

    @Test
    void deveRecusarFuncionarioInativo() {
        Funcionario funcionario = new Funcionario("Roberto", "Auxiliar", Perfil.CAIXA, "roberto", "hash");
        funcionario.setAtivo(false);
        when(repositorio.findByLogin("roberto")).thenReturn(Optional.of(funcionario));

        assertThatThrownBy(() -> servico.loadUserByUsername("roberto"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Usuário inativo ou credenciais inválidas.");
    }

    @Test
    void deveRecusarLoginInexistente() {
        when(repositorio.findByLogin("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.loadUserByUsername("fantasma"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
