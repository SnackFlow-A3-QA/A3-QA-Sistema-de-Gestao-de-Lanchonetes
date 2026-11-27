package br.com.snackflow.cozinha;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Acesso a dados do modulo cozinha via JdbcTemplate (view vw_cozinha e procedure).
 */
@Repository
public class CozinhaRepository {

    private static final String SQL_LISTAR_FILA = """
            SELECT id_pedido, data_hora, cliente, obs_pedido, produto, quantidade, opcoes, obs_item
              FROM vw_cozinha
            """;

    private static final String SQL_MARCAR_PRONTO = "CALL sp_alterar_status_pedido(?, 'PRONTO', NULL, ?)";

    private static final RowMapper<LinhaCozinha> MAPA_LINHA = CozinhaRepository::mapearLinha;

    private final JdbcTemplate jdbc;

    public CozinhaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LinhaCozinha> listarLinhasFila() {
        return jdbc.query(SQL_LISTAR_FILA, MAPA_LINHA);
    }

    /** Chama a procedure fora de @Transactional para concluir o preparo do pedido. */
    public void marcarPedidoComoPronto(Integer idPedido, Integer idFuncionario) {
        jdbc.update(SQL_MARCAR_PRONTO, idPedido, idFuncionario);
    }

    private static LinhaCozinha mapearLinha(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("data_hora");
        return new LinhaCozinha(
                rs.getInt("id_pedido"),
                ts == null ? null : ts.toLocalDateTime(),
                rs.getString("cliente"),
                rs.getString("obs_pedido"),
                rs.getString("produto"),
                rs.getInt("quantidade"),
                rs.getString("opcoes"),
                rs.getString("obs_item"));
    }
}
