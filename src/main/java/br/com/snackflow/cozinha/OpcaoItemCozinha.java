package br.com.snackflow.cozinha;

/** Opcao de preparo interpretada da coluna texto opcoes da vw_cozinha (+ ou SEM). */
public record OpcaoItemCozinha(boolean adicional, String texto) {
}
