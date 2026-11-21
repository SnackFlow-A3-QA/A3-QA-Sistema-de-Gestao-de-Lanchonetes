package br.com.snackflow.relatorio;

import java.time.LocalDate;
import java.util.List;

/** Relatório já gerado: período efetivamente usado + tabela de resultados. */
public record ResultadoRelatorio(TipoRelatorio tipo,
                                 LocalDate inicio,
                                 LocalDate fim,
                                 List<String> colunas,
                                 List<List<Object>> linhas) {
}
