package br.com.snackflow.relatorio;

import br.com.snackflow.common.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ConcurrentModel;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 30);

    @Mock
    private RelatorioService service;

    @InjectMocks
    private RelatorioController controller;

    private final ConcurrentModel model = new ConcurrentModel();

    private static ResultadoRelatorio resultado(TipoRelatorio tipo) {
        return new ResultadoRelatorio(tipo, INICIO, FIM, List.of("Data"), List.of());
    }

    @Test
    void exibeORelatorioGerado() {
        ResultadoRelatorio esperado = resultado(TipoRelatorio.MAIS_VENDIDOS);
        when(service.gerar(TipoRelatorio.MAIS_VENDIDOS, INICIO, FIM)).thenReturn(esperado);

        String view = controller.exibir(TipoRelatorio.MAIS_VENDIDOS, INICIO, FIM, model);

        assertThat(view).isEqualTo(RelatorioController.VIEW);
        assertThat(model.getAttribute("resultado")).isEqualTo(esperado);
        assertThat(model.getAttribute("tipos")).isEqualTo(TipoRelatorio.values());
        assertThat(model.getAttribute("erro")).isNull();
    }

    @Test
    void exibeErroEMantemOsFiltrosQuandoOPeriodoEInvalido() {
        when(service.gerar(TipoRelatorio.FATURAMENTO, FIM, INICIO))
                .thenThrow(new RegraNegocioException("A data inicial não pode ser maior que a data final."));

        controller.exibir(TipoRelatorio.FATURAMENTO, FIM, INICIO, model);

        ResultadoRelatorio resultado = (ResultadoRelatorio) model.getAttribute("resultado");
        assertThat(model.getAttribute("erro")).isNotNull();
        assertThat(resultado.inicio()).isEqualTo(FIM);
        assertThat(resultado.linhas()).isEmpty();
    }

    @Test
    void exportaCsvComNomeDeArquivoDoRelatorio() {
        ResultadoRelatorio gerado = resultado(TipoRelatorio.PERDAS_E_AJUSTES);
        when(service.gerar(TipoRelatorio.PERDAS_E_AJUSTES, INICIO, FIM)).thenReturn(gerado);
        when(service.exportarCsv(gerado)).thenReturn("a;b".getBytes());

        ResponseEntity<byte[]> resposta = controller.exportarCsv(TipoRelatorio.PERDAS_E_AJUSTES, INICIO, FIM);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo("a;b".getBytes());
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("relatorio-perdas_e_ajustes.csv");
        assertThat(resposta.getHeaders().getContentType().toString()).startsWith("text/csv");
    }

    @Test
    void respondeBadRequestParaRegraDeNegocio() {
        ResponseEntity<String> resposta = controller.regraNegocio(new RegraNegocioException("Período inválido."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).isEqualTo("Período inválido.");
    }
}
