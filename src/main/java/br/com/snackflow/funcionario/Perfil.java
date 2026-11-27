package br.com.snackflow.funcionario;

/** Perfis de acesso (coluna funcionarios.perfil) e a tela inicial de cada um. */
public enum Perfil {
    ADMIN("/pdv"),
    CAIXA("/pdv"),
    COZINHA("/cozinha"),
    ESTOQUE("/estoque");

    private static final String PREFIXO_AUTORIDADE = "ROLE_";

    private final String paginaInicial;

    Perfil(String paginaInicial) {
        this.paginaInicial = paginaInicial;
    }

    public String getPaginaInicial() {
        return paginaInicial;
    }

    /** Autoridade do Spring Security, por exemplo ROLE_CAIXA. */
    public String autoridade() {
        return PREFIXO_AUTORIDADE + name();
    }
}
