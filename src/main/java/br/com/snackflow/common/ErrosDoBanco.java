package br.com.snackflow.common;

import java.sql.SQLException;
import org.springframework.dao.DataAccessException;

/**
 * Converte erros do banco em {@link RegraNegocioException}.
 *
 * <p>Os triggers e a procedure do banco sinalizam violações de regra com
 * {@code SIGNAL SQLSTATE '45000'} e uma mensagem já amigável. Esses erros chegam à
 * aplicação como {@link DataAccessException}; aqui a mensagem do trigger é reaproveitada.
 * Qualquer outro erro vira uma mensagem genérica, sem expor detalhes internos.
 */
public final class ErrosDoBanco {

    static final String SQLSTATE_REGRA_DE_NEGOCIO = "45000";
    static final String MENSAGEM_GENERICA = "Não foi possível concluir a operação. Tente novamente.";

    private ErrosDoBanco() {
    }

    public static RegraNegocioException converter(DataAccessException erro) {
        return new RegraNegocioException(extrairMensagem(erro), erro);
    }

    static String extrairMensagem(Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            if (atual instanceof SQLException sql && SQLSTATE_REGRA_DE_NEGOCIO.equals(sql.getSQLState())
                    && sql.getMessage() != null && !sql.getMessage().isBlank()) {
                return sql.getMessage();
            }
            atual = atual.getCause() == atual ? null : atual.getCause();
        }
        return MENSAGEM_GENERICA;
    }
}
