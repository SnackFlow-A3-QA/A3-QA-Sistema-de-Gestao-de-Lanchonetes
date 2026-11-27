package br.com.snackflow.common;

/**
 * Violação de regra de negócio com mensagem segura para exibir ao usuário.
 * Lançada pelos Services; o Controller captura e mostra a mensagem na tela.
 */
public class RegraNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }

    public RegraNegocioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
