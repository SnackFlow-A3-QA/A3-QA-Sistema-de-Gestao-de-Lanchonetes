package br.com.snackflow.cliente;

import java.util.Optional;

/**
 * Telefone brasileiro: 10 ou 11 dígitos (após remover a pontuação), DDD de dois dígitos iniciado
 * de 1 a 9 e, com 11 dígitos, nono dígito 9 depois do DDD. Formato canônico:
 * {@code (DD) XXXX-XXXX} ou {@code (DD) 9XXXX-XXXX}.
 */
final class Telefone {

    private Telefone() {
    }

    static Optional<String> formatar(String entrada) {
        if (entrada == null) {
            return Optional.empty();
        }
        String digitos = entrada.replaceAll("\\D", "");
        if (digitos.length() < 10 || digitos.length() > 11 || digitos.charAt(0) == '0') {
            return Optional.empty();
        }
        String ddd = digitos.substring(0, 2);
        if (digitos.length() == 10) {
            return Optional.of("(" + ddd + ") " + digitos.substring(2, 6) + "-" + digitos.substring(6));
        }
        if (digitos.charAt(2) != '9') {
            return Optional.empty();
        }
        return Optional.of("(" + ddd + ") " + digitos.substring(2, 7) + "-" + digitos.substring(7));
    }
}
