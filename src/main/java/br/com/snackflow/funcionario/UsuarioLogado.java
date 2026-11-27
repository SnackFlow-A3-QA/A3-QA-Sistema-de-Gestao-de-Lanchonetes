package br.com.snackflow.funcionario;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal guardado na sessão após o login. Leva o id do funcionário, que os módulos usam em
 * pedidos.id_funcionario, nas movimentações de estoque e na procedure sp_alterar_status_pedido.
 *
 * <p>Nos controllers: {@code @AuthenticationPrincipal UsuarioLogado usuario}.
 */
public class UsuarioLogado implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final Integer id;
    private final String nome;
    private final String login;
    private final String senhaHash;
    private final Perfil perfil;
    private final boolean ativo;

    public UsuarioLogado(Integer id, String nome, String login, String senhaHash, Perfil perfil, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.login = login;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.ativo = ativo;
    }

    public static UsuarioLogado de(Funcionario funcionario) {
        return new UsuarioLogado(funcionario.getId(), funcionario.getNome(), funcionario.getLogin(),
                funcionario.getSenhaHash(), funcionario.getPerfil(), funcionario.isAtivo());
    }

    public Integer getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(perfil.autoridade()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }
}
