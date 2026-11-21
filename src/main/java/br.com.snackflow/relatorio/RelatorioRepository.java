package br.com.snackflow.relatorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Consultas de leitura dos relatórios (SQL em {@link TipoRelatorio}). */
@Repository
public class RelatorioRepository {

    /** Resultado bruto de uma consulta. */
    public record Tabela(List<String> colunas, List<List<Object>> linhas) {
    }

    private final JdbcTemplate jdbc;

    public RelatorioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** "de" é inclusivo e "ate" é exclusivo; ignorados nos relatórios sem período. */
    public Tabela consultar(TipoRelatorio tipo, LocalDateTime de, LocalDateTime ate) {
        Object[] parametros = tipo.isUsaPeriodo() ? new Object[]{de, ate} : new Object[0];
        ResultSetExtractor<Tabela> extrator = rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int total = meta.getColumnCount();
            List<String> colunas = new ArrayList<>(total);
            for (int i = 1; i <= total; i++) {
                colunas.add(rotulo(meta.getColumnLabel(i)));
            }
            List<List<Object>> linhas = new ArrayList<>();
            while (rs.next()) {
                List<Object> linha = new ArrayList<>(total); // ArrayList aceita valores NULL do banco
                for (int i = 1; i <= total; i++) {
                    linha.add(rs.getObject(i));
                }
                linhas.add(linha);
            }
            return new Tabela(colunas, linhas);
        };
        return jdbc.query(tipo.getSql(), extrator, parametros);
    }

    /** qtd_vendida -> "Qtd vendida". */
    private static String rotulo(String coluna) {
        String texto = coluna.replace('_', ' ');
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
