package br.com.snackflow.pedido;

import br.com.snackflow.common.RegraNegocioException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Regras de negocio do carrinho do PDV (singleton sem estado; o carrinho vive na sessao HTTP).
 */
@Service
public class PedidoCarrinhoService {

    static final int TAMANHO_MAXIMO_OBSERVACAO = 255;

    static final String MENSAGEM_CARRINHO_INVALIDO = "Carrinho inválido.";
    static final String MENSAGEM_PRODUTO_NAO_ENCONTRADO = "Produto não encontrado ou inativo.";
    static final String MENSAGEM_PRODUTO_INDISPONIVEL = "Produto indisponível no momento.";
    static final String MENSAGEM_ESTOQUE_INSUFICIENTE =
            "Quantidade solicitada excede o estoque disponível para este produto.";
    static final String MENSAGEM_QUANTIDADE_INVALIDA = "Informe uma quantidade maior que zero.";
    static final String MENSAGEM_OPCAO_INVALIDA = "Opção inválida ou inativa para este produto.";
    static final String MENSAGEM_ITEM_NAO_ENCONTRADO = "Item não encontrado no carrinho.";
    static final String MENSAGEM_OBSERVACAO_LONGA = "A observação deve ter no máximo 255 caracteres.";
    static final String MENSAGEM_CLIENTE_INVALIDO = "Cliente não encontrado ou inativo.";
    static final String MENSAGEM_PAGAMENTO_OBRIGATORIO = "Selecione a forma de pagamento.";

    private final PedidoRepository repositorio;

    public PedidoCarrinhoService(PedidoRepository repositorio) {
        this.repositorio = repositorio;
    }

    public List<ProdutoDisponivel> listarProdutosDoCardapio() {
        return repositorio.listarProdutosAtivos();
    }

    public Map<Integer, List<OpcaoProduto>> listarOpcoesAgrupadasPorProduto() {
        return repositorio.listarTodasOpcoesAtivas().stream()
                .collect(Collectors.groupingBy(OpcaoProduto::idProduto));
    }

    public List<OpcaoProduto> listarOpcoesDoProduto(Integer idProduto) {
        if (idProduto == null || idProduto <= 0) {
            throw new RegraNegocioException(MENSAGEM_PRODUTO_NAO_ENCONTRADO);
        }
        buscarProdutoAtivo(idProduto);
        return repositorio.listarOpcoesAtivasPorProduto(idProduto);
    }

    public void adicionarItem(
            CarrinhoPedido carrinho,
            Integer idProduto,
            int quantidade,
            List<Integer> idsOpcoes,
            String observacao) {
        validarCarrinho(carrinho);
        if (idProduto == null || idProduto <= 0) {
            throw new RegraNegocioException(MENSAGEM_PRODUTO_NAO_ENCONTRADO);
        }
        if (quantidade <= 0) {
            throw new RegraNegocioException(MENSAGEM_QUANTIDADE_INVALIDA);
        }

        ProdutoDisponivel produto = buscarProdutoAtivo(idProduto);
        validarDisponibilidade(produto, carrinho.quantidadeDoProduto(idProduto) + quantidade);

        String obsNormalizada = normalizarObservacao(observacao);
        List<OpcaoCarrinho> opcoesSelecionadas = resolverOpcoes(idProduto, idsOpcoes);

        int indiceExistente = encontrarItemIdentico(carrinho, idProduto, opcoesSelecionadas, obsNormalizada);
        if (indiceExistente >= 0) {
            ItemCarrinho atual = carrinho.getItens().get(indiceExistente);
            carrinho.substituirItem(indiceExistente, atual.comQuantidade(atual.quantidade() + quantidade));
            return;
        }

        ItemCarrinho novoItem = new ItemCarrinho(
                UUID.randomUUID().toString(),
                produto.idProduto(),
                produto.nome(),
                produto.preco(),
                quantidade,
                opcoesSelecionadas,
                obsNormalizada);
        carrinho.adicionarItem(novoItem);
    }

    public void alterarQuantidade(CarrinhoPedido carrinho, String itemUid, int novaQuantidade) {
        validarCarrinho(carrinho);
        if (novaQuantidade <= 0) {
            throw new RegraNegocioException(MENSAGEM_QUANTIDADE_INVALIDA);
        }

        int indice = exigirIndiceItem(carrinho, itemUid);
        ItemCarrinho itemAtual = carrinho.getItens().get(indice);
        if (novaQuantidade > itemAtual.quantidade()) {
            ProdutoDisponivel produto = buscarProdutoAtivo(itemAtual.idProduto());
            int totalOutrosItens = carrinho.quantidadeDoProduto(itemAtual.idProduto()) - itemAtual.quantidade();
            validarDisponibilidade(produto, totalOutrosItens + novaQuantidade);
        }
        carrinho.substituirItem(indice, itemAtual.comQuantidade(novaQuantidade));
    }

    public void removerItem(CarrinhoPedido carrinho, String itemUid) {
        validarCarrinho(carrinho);
        int indice = exigirIndiceItem(carrinho, itemUid);
        carrinho.removerItemPorIndice(indice);
    }

    public void atualizarOpcoesEObservacao(
            CarrinhoPedido carrinho,
            String itemUid,
            List<Integer> idsOpcoes,
            String observacao) {
        validarCarrinho(carrinho);
        int indice = exigirIndiceItem(carrinho, itemUid);
        ItemCarrinho itemAtual = carrinho.getItens().get(indice);

        String obsNormalizada = normalizarObservacao(observacao);
        List<OpcaoCarrinho> opcoesSelecionadas = resolverOpcoes(itemAtual.idProduto(), idsOpcoes);
        carrinho.substituirItem(indice, itemAtual.comOpcoesEObservacao(opcoesSelecionadas, obsNormalizada));
    }

    public void definirCliente(CarrinhoPedido carrinho, Integer idCliente) {
        validarCarrinho(carrinho);
        if (idCliente == null) {
            carrinho.definirCliente(null, null);
            return;
        }
        if (idCliente <= 0) {
            throw new RegraNegocioException(MENSAGEM_CLIENTE_INVALIDO);
        }
        String nomeCliente = repositorio.buscarNomeClienteAtivo(idCliente)
                .orElseThrow(() -> new RegraNegocioException(MENSAGEM_CLIENTE_INVALIDO));
        carrinho.definirCliente(idCliente, nomeCliente);
    }

    public void definirDadosFinalizacao(
            CarrinhoPedido carrinho, FormaPagamento formaPagamento, String observacao) {
        validarCarrinho(carrinho);
        if (formaPagamento == null) {
            throw new RegraNegocioException(MENSAGEM_PAGAMENTO_OBRIGATORIO);
        }
        String obsNormalizada = normalizarObservacao(observacao);
        carrinho.definirFormaPagamento(formaPagamento);
        carrinho.definirObservacao(obsNormalizada);
    }

    public void limparCarrinho(CarrinhoPedido carrinho) {
        validarCarrinho(carrinho);
        carrinho.limpar();
    }

    private ProdutoDisponivel buscarProdutoAtivo(Integer idProduto) {
        ProdutoDisponivel produto = repositorio.buscarProdutoDisponivel(idProduto)
                .orElseThrow(() -> new RegraNegocioException(MENSAGEM_PRODUTO_NAO_ENCONTRADO));
        if (!produto.ativo()) {
            throw new RegraNegocioException(MENSAGEM_PRODUTO_NAO_ENCONTRADO);
        }
        return produto;
    }

    private void validarDisponibilidade(ProdutoDisponivel produto, int quantidadeTotalDesejada) {
        if (produto.disponivel() <= 0) {
            throw new RegraNegocioException(MENSAGEM_PRODUTO_INDISPONIVEL);
        }
        if (quantidadeTotalDesejada > produto.disponivel()) {
            throw new RegraNegocioException(MENSAGEM_ESTOQUE_INSUFICIENTE);
        }
    }

    private List<OpcaoCarrinho> resolverOpcoes(Integer idProduto, List<Integer> idsOpcoes) {
        if (idsOpcoes == null || idsOpcoes.isEmpty()) {
            return List.of();
        }
        List<Integer> idsDistintos = idsOpcoes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (idsDistintos.size() != idsOpcoes.size()) {
            throw new RegraNegocioException(MENSAGEM_OPCAO_INVALIDA);
        }

        Map<Integer, OpcaoProduto> mapaAtivas = repositorio.listarOpcoesAtivasPorProduto(idProduto).stream()
                .collect(Collectors.toMap(OpcaoProduto::idOpcao, Function.identity()));

        return idsDistintos.stream()
                .map(id -> {
                    OpcaoProduto op = mapaAtivas.get(id);
                    if (op == null || !op.ativo() || !idProduto.equals(op.idProduto())) {
                        throw new RegraNegocioException(MENSAGEM_OPCAO_INVALIDA);
                    }
                    return new OpcaoCarrinho(op.idOpcao(), op.tipo(), op.nome(), op.precoAdicional());
                })
                .sorted(Comparator.comparing(OpcaoCarrinho::tipo).thenComparing(OpcaoCarrinho::nome))
                .toList();
    }

    private int encontrarItemIdentico(
            CarrinhoPedido carrinho,
            Integer idProduto,
            List<OpcaoCarrinho> opcoes,
            String observacao) {
        Set<Integer> idsSelecionados = opcoes.stream().map(OpcaoCarrinho::idOpcao).collect(Collectors.toSet());
        List<ItemCarrinho> itens = carrinho.getItens();
        for (int i = 0; i < itens.size(); i++) {
            ItemCarrinho item = itens.get(i);
            Set<Integer> idsItem = item.opcoes().stream().map(OpcaoCarrinho::idOpcao).collect(Collectors.toSet());
            if (idProduto.equals(item.idProduto())
                    && idsSelecionados.equals(idsItem)
                    && Objects.equals(observacao, item.observacao())) {
                return i;
            }
        }
        return -1;
    }

    private int exigirIndiceItem(CarrinhoPedido carrinho, String itemUid) {
        if (itemUid == null || itemUid.isBlank()) {
            throw new RegraNegocioException(MENSAGEM_ITEM_NAO_ENCONTRADO);
        }
        int indice = carrinho.indicePorUid(itemUid);
        if (indice < 0) {
            throw new RegraNegocioException(MENSAGEM_ITEM_NAO_ENCONTRADO);
        }
        return indice;
    }

    private static void validarCarrinho(CarrinhoPedido carrinho) {
        if (carrinho == null) {
            throw new RegraNegocioException(MENSAGEM_CARRINHO_INVALIDO);
        }
    }

    private static String normalizarObservacao(String observacao) {
        if (observacao == null) {
            return null;
        }
        String limpa = observacao.trim();
        if (limpa.isEmpty()) {
            return null;
        }
        if (limpa.length() > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new RegraNegocioException(MENSAGEM_OBSERVACAO_LONGA);
        }
        return limpa;
    }
}
