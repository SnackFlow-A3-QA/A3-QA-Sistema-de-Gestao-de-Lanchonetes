package br.com.snackflow.cliente;

/** Utilitário de CPF. A regra de validação é a mesma da função fn_cpf_valido do banco. */
public final class Cpf {

    private Cpf() {
    }

    /** Remove tudo que não for dígito; null vira "". */
    public static String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    /** CPF com 11 dígitos, dígitos verificadores corretos e sem sequência repetida. */
    public static boolean valido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}")) {
            return false;
        }
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }
        return digito(cpf, 9) == cpf.charAt(9) - '0'
                && digito(cpf, 10) == cpf.charAt(10) - '0';
    }

    /** 52998224725 -> 529.982.247-25; qualquer outro valor é devolvido como está. */
    public static String formatar(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}")) {
            return cpf == null ? "" : cpf;
        }
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "."
                + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private static int digito(String cpf, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (cpf.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
