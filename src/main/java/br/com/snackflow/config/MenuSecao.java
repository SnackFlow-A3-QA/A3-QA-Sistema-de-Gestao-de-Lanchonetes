package br.com.snackflow.config;

import java.util.List;

/** Grupo do menu lateral (OPERAÇÃO, ADMINISTRAÇÃO). */
public record MenuSecao(String titulo, List<MenuItem> itens) {
}
