package br.com.snackflow.funcionario;

/** Linha da listagem. Nunca carrega o hash da senha. */
public record FuncionarioLinha(Integer id, String nome, String cargo, String login, Perfil perfil, boolean ativo) {
}
