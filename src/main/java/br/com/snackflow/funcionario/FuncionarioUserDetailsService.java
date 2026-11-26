package br.com.snackflow.funcionario;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Autentica pelos funcionários do banco: só entram os ativos (funcionarios.ativo = 1). */
@Service
public class FuncionarioUserDetailsService implements UserDetailsService {

    static final String MENSAGEM_RECUSADO = "Usuário inativo ou credenciais inválidas.";

    private final FuncionarioRepository repositorio;

    public FuncionarioUserDetailsService(FuncionarioRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public UserDetails loadUserByUsername(String login) {
        return repositorio.findByLogin(login)
                .filter(Funcionario::isAtivo)
                .map(UsuarioLogado::de)
                .orElseThrow(() -> new UsernameNotFoundException(MENSAGEM_RECUSADO));
    }
}
