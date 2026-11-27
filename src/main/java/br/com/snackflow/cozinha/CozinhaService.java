package br.com.snackflow.cozinha;

import br.com.snackflow.common.ErrosDoBanco;
import br.com.snackflow.common.RegraNegocioException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Regras da fila da cozinha (KDS): ordenacao cronologica no Service e marcacao de PRONTO.
 */
@Service
public class CozinhaService {

    static final String MENSAGEM_PEDIDO_INVALIDO = "Pedido inválido.";
    static final String MENSAGEM_FUNCIONARIO_INVALIDO = "Funcionário não identificado.";
    private static final Pattern SEPARADOR_OPCOES = Pattern.compile(", (?=\\+ |SEM )");

    private final CozinhaRepository repositorio;

    public CozinhaService(CozinhaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Agrupa as linhas da vw_cozinha por pedido, reordena do mais antigo para o mais novo
     * no Service e destaca o primeiro pedido da fila.
     */
    public List<PedidoCozinha> listarFilaOrdenada() {
        List<LinhaCozinha> linhas = repositorio.listarLinhasFila();
        if (linhas.isEmpty()) {
            return List.of();
        }

        Map<Integer, GrupoPedido> grupos = new LinkedHashMap<>();
        for (LinhaCozinha linha : linhas) {
            GrupoPedido grupo = grupos.computeIfAbsent(
                    linha.idPedido(),
                    id -> new GrupoPedido(id, linha.dataHora(), linha.cliente(), linha.observacaoPedido()));
            grupo.itens.add(new ItemPedidoCozinha(
                    linha.produto(),
                    linha.quantidade(),
                    interpretarOpcoes(linha.opcoes()),
                    linha.observacaoItem()));
        }

        List<GrupoPedido> ordenados = grupos.values().stream()
                .sorted(Comparator
                        .comparing(GrupoPedido::dataHora, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(GrupoPedido::idPedido))
                .toList();

        List<PedidoCozinha> resultado = new ArrayList<>(ordenados.size());
        for (int i = 0; i < ordenados.size(); i++) {
            GrupoPedido g = ordenados.get(i);
            resultado.add(new PedidoCozinha(
                    g.idPedido,
                    g.dataHora,
                    g.cliente,
                    g.observacao,
                    g.itens,
                    i == 0));
        }
        return resultado;
    }

    public void marcarComoPronto(Integer idPedido, Integer idFuncionario) {
        if (idPedido == null || idPedido <= 0) {
            throw new RegraNegocioException(MENSAGEM_PEDIDO_INVALIDO);
        }
        if (idFuncionario == null || idFuncionario <= 0) {
            throw new RegraNegocioException(MENSAGEM_FUNCIONARIO_INVALIDO);
        }
        try {
            repositorio.marcarPedidoComoPronto(idPedido, idFuncionario);
        } catch (DataAccessException erro) {
            throw ErrosDoBanco.converter(erro);
        }
    }

    static List<OpcaoItemCozinha> interpretarOpcoes(String opcoesTexto) {
        if (opcoesTexto == null || opcoesTexto.isBlank()) {
            return List.of();
        }
        String[] partes = SEPARADOR_OPCOES.split(opcoesTexto.trim());
        List<OpcaoItemCozinha> opcoes = new ArrayList<>(partes.length);
        for (String parte : partes) {
            String limpa = parte.trim();
            if (!limpa.isEmpty()) {
                boolean adicional = limpa.startsWith("+ ");
                opcoes.add(new OpcaoItemCozinha(adicional, limpa));
            }
        }
        return opcoes;
    }

    private static final class GrupoPedido {
        private final Integer idPedido;
        private final java.time.LocalDateTime dataHora;
        private final String cliente;
        private final String observacao;
        private final List<ItemPedidoCozinha> itens = new ArrayList<>();

        private GrupoPedido(
                Integer idPedido,
                java.time.LocalDateTime dataHora,
                String cliente,
                String observacao) {
            this.idPedido = idPedido;
            this.dataHora = dataHora;
            this.cliente = cliente;
            this.observacao = observacao;
        }

        private Integer idPedido() {
            return idPedido;
        }

        private java.time.LocalDateTime dataHora() {
            return dataHora;
        }
    }
}
