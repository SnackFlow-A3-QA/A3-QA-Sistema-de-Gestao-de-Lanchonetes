package br.com.snackflow.relatorio;

import br.com.snackflow.common.exception.RegraNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RelatorioService {

    private static final int DIAS_PADRAO = 30;
    private static final String SEPARADOR = ";";
    private static final String QUEBRA_LINHA = "\r\n";

    private final RelatorioRepository repository;

    public RelatorioService(RelatorioRepository repository) {
        this.repository = repository;
    }

    /** Sem datas, usa os últimos 30 dias (hoje incluído). Fim é inclusivo. */
    @Transactional(readOnly = true)
    public ResultadoRelatorio gerar(TipoRelatorio tipo, LocalDate inicio, LocalDate fim) {
        LocalDate dataFim = fim != null ? fim : LocalDate.now();
        LocalDate dataInicio = inicio != null ? inicio : dataFim.minusDays(DIAS_PADRAO - 1L);
        if (dataInicio.isAfter(dataFim)) {
            throw new RegraNegocioException("A data inicial não pode ser maior que a data final.");
        }
        RelatorioRepository.Tabela tabela = repository.consultar(
                tipo, dataInicio.atStartOfDay(), dataFim.plusDays(1).atStartOfDay());
        return new ResultadoRelatorio(tipo, dataInicio, dataFim, tabela.colunas(), tabela.linhas());
    }

    /** CSV em UTF-8 com BOM e ";" como separador (abre direto no Excel em português). */
    public byte[] exportarCsv(ResultadoRelatorio resultado) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(juntar(resultado.colunas())).append(QUEBRA_LINHA);
        for (List<Object> linha : resultado.linhas()) {
            csv.append(juntar(linha)).append(QUEBRA_LINHA);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String juntar(List<?> valores) {
        return valores.stream().map(RelatorioService::celula).collect(Collectors.joining(SEPARADOR));
    }

    private static String celula(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor instanceof BigDecimal decimal
                ? decimal.toPlainString().replace('.', ',')
                : valor.toString();
        // evita que o Excel execute como fórmula um texto que começa com = + - @
        if (valor instanceof CharSequence && !texto.isEmpty() && "=+-@".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.contains(SEPARADOR) || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
