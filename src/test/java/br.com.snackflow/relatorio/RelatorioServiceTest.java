package br.com.snackflow.relatorio;

import br.com.snackflow.common.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

    @Mock
    private RelatorioRepository repository;

    @InjectMocks
    private RelatorioService service;

    private static List<List<Object>> linhas(Object... valores) {
        List<List<Object>> linhas = new ArrayList<>();
        linhas.add(new ArrayList<>(Arrays.asList(valores)));
        return linhas;
    }

    @Test
    void geraRelatorioComFimInclusivoNoPeriodo() {
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 30);
        when(repository.consultar(TipoRelatorio.FATURAMENTO,
                inicio.atStartOfDay(), LocalDate.of(2026, 12, 1).atStartOfDay()))
                .thenReturn(new RelatorioRepository.Tabela(List.of("Data", "Pedidos"), linhas("2026-11-01", 3)));

        ResultadoRelatorio resultado = service.gerar(TipoRelatorio.FATURAMENTO, inicio, fim);

        assertThat(resultado.tipo()).isEqualTo(TipoRelatorio.FATURAMENTO);
        assertThat(resultado.inicio()).isEqualTo(inicio);
        assertThat(resultado.fim()).isEqualTo(fim);
        assertThat(resultado.colunas()).containsExactly("Data", "Pedidos");
        assertThat(resultado.linhas()).hasSize(1);
    }

    @Test
    void semDatasUsaOsUltimosTrintaDias() {
        when(repository.consultar(eq(TipoRelatorio.POSICAO_ESTOQUE), any(), any()))
                .thenReturn(new RelatorioRepository.Tabela(List.of(), List.of()));

        ResultadoRelatorio resultado = service.gerar(TipoRelatorio.POSICAO_ESTOQUE, null, null);

        assertThat(resultado.fim()).isEqualTo(LocalDate.now());
        assertThat(resultado.inicio()).isEqualTo(LocalDate.now().minusDays(29));
        verify(repository).consultar(eq(TipoRelatorio.POSICAO_ESTOQUE),
                eq(resultado.inicio().atStartOfDay()), eq(resultado.fim().plusDays(1).atStartOfDay()));
    }

    @Test
    void rejeitaPeriodoInvertido() {
        LocalDate inicio = LocalDate.of(2026, 11, 10);
        LocalDate fim = LocalDate.of(2026, 11, 1);

        assertThatThrownBy(() -> service.gerar(TipoRelatorio.MAIS_VENDIDOS, inicio, fim))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("data inicial");
    }

    @Test
    void exportaCsvComBomSeparadorPontoEVirgulaEEscapes() {
        ResultadoRelatorio resultado = new ResultadoRelatorio(TipoRelatorio.FATURAMENTO,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
                List.of("Produto", "Valor", "Obs"),
                linhas("X-Burger; \"duplo\"", new BigDecimal("12.50"), null));

        String csv = new String(service.exportarCsv(resultado), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFFProduto;Valor;Obs\r\n");
        assertThat(csv).contains("\"X-Burger; \"\"duplo\"\"\";12,50;\r\n");
    }

    @Test
    void neutralizaTextoQueParecaFormula() {
        ResultadoRelatorio resultado = new ResultadoRelatorio(TipoRelatorio.PERDAS_E_AJUSTES,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
                List.of("Obs"), linhas("=1+1"));

        String csv = new String(service.exportarCsv(resultado), StandardCharsets.UTF_8);

        assertThat(csv).contains("'=1+1");
    }

    @Test
    void relatoriosDeEstoqueNaoUsamPeriodo() {
        assertThat(TipoRelatorio.POSICAO_ESTOQUE.isUsaPeriodo()).isFalse();
        assertThat(TipoRelatorio.FATURAMENTO.isUsaPeriodo()).isTrue();
        assertThat(TipoRelatorio.FATURAMENTO.getTitulo()).isNotBlank();
    }
}
