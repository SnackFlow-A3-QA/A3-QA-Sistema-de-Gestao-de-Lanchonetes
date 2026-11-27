package br.com.snackflow.config;

/** Item do menu lateral. {@code ativo} indica a tela atual (destaque laranja). */
public record MenuItem(String rota, String rotulo, String icone, boolean ativo) {
}
