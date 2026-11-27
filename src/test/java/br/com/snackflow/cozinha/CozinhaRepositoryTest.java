package br.com.snackflow.cozinha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class CozinhaRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private CozinhaRepository repositorio;

    @Test
    @SuppressWarnings("unchecked")
    void deveMapearLinhasDaViewVwCozinhaEChamarProcedureParaPronto() throws Exception {
        ArgumentCaptor<RowMapper<LinhaCozinha>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(anyString(), captor.capture())).thenReturn(List.of());

        repositorio.listarLinhasFila();

        LocalDateTime hora = LocalDateTime.of(2026, 11, 27, 18, 30);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id_pedido")).thenReturn(14);
        when(rs.getTimestamp("data_hora")).thenReturn(Timestamp.valueOf(hora));
        when(rs.getString("cliente")).thenReturn("Mariana");
        when(rs.getString("obs_pedido")).thenReturn("Sem talheres");
        when(rs.getString("produto")).thenReturn("X-Bacon");
        when(rs.getInt("quantidade")).thenReturn(2);
        when(rs.getString("opcoes")).thenReturn("+ Bacon, SEM Cebola");
        when(rs.getString("obs_item")).thenReturn("Bem passado");

        LinhaCozinha linha = captor.getValue().mapRow(rs, 0);
        assertThat(linha).isNotNull();
        assertThat(linha.idPedido()).isEqualTo(14);
        assertThat(linha.dataHora()).isEqualTo(hora);
        assertThat(linha.cliente()).isEqualTo("Mariana");
        assertThat(linha.observacaoPedido()).isEqualTo("Sem talheres");
        assertThat(linha.produto()).isEqualTo("X-Bacon");
        assertThat(linha.quantidade()).isEqualTo(2);
        assertThat(linha.opcoes()).isEqualTo("+ Bacon, SEM Cebola");
        assertThat(linha.observacaoItem()).isEqualTo("Bem passado");

        ResultSet rsSemHora = mock(ResultSet.class);
        when(rsSemHora.getTimestamp("data_hora")).thenReturn(null);
        assertThat(captor.getValue().mapRow(rsSemHora, 1).dataHora()).isNull();

        repositorio.marcarPedidoComoPronto(14, 3);
        verify(jdbc).update("CALL sp_alterar_status_pedido(?, 'PRONTO', NULL, ?)", 14, 3);
    }
}
