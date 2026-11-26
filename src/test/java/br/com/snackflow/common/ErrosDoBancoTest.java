package br.com.snackflow.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

class ErrosDoBancoTest {

    @Test
    void deveReaproveitarMensagemDoTriggerComSqlState45000() {
        SQLException trigger = new SQLException("Estoque insuficiente (ingrediente 4)", "45000");
        DataIntegrityViolationException erro = new DataIntegrityViolationException("falha", trigger);

        RegraNegocioException regra = ErrosDoBanco.converter(erro);

        assertThat(regra).hasMessage("Estoque insuficiente (ingrediente 4)").hasCause(erro);
    }

    @Test
    void deveEncontrarOTriggerEmCausasAninhadas() {
        SQLException trigger = new SQLException("CPF invalido", "45000");
        DataIntegrityViolationException erro =
                new DataIntegrityViolationException("externo", new IllegalStateException("meio", trigger));

        assertThat(ErrosDoBanco.extrairMensagem(erro)).isEqualTo("CPF invalido");
    }

    @Test
    void naoDeveExporMensagemDeErroTecnicoDoBanco() {
        SQLException tecnico = new SQLException("Duplicate entry 'x' for key 'uk_produtos_nome'", "23000");

        assertThat(ErrosDoBanco.extrairMensagem(new DataIntegrityViolationException("falha", tecnico)))
                .isEqualTo(ErrosDoBanco.MENSAGEM_GENERICA);
    }

    @Test
    void deveUsarMensagemGenericaQuandoNaoHouverCausaSql() {
        assertThat(ErrosDoBanco.extrairMensagem(new QueryTimeoutException("tempo esgotado")))
                .isEqualTo(ErrosDoBanco.MENSAGEM_GENERICA);
    }

    @Test
    void deveIgnorarSqlState45000SemMensagem() {
        SQLException vazia = new SQLException("  ", "45000");

        assertThat(ErrosDoBanco.extrairMensagem(vazia)).isEqualTo(ErrosDoBanco.MENSAGEM_GENERICA);
    }
}
