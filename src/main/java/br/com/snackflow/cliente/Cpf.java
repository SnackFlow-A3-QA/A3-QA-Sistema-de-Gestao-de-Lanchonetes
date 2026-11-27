package br.com.snackflow.cliente;

/** Validação de CPF com a mesma regra de fn_cpf_valido (database/lanchonete.sql). */
final class Cpf {

    private Cpf() {
    }

    static String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    /** Recebe só dígitos: 11 dígitos, não todos iguais e com os dois verificadores corretos. */
    static boolean valido(String digitos) {
        if (digitos == null || !digitos.matches("[0-9]{11}")) {
            return false;
        }
        if (digitos.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(digitos.substring(0, 9), 10) == digitos.charAt(9) - '0'
                && digitoVerificador(digitos.substring(0, 10), 11) == digitos.charAt(10) - '0';
    }

    private static int digitoVerificador(String base, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) {
            soma += (base.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
