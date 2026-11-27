package br.com.snackflow.pedido;

import br.com.snackflow.common.ErrosDoBanco;
import br.com.snackflow.common.RegraNegocioException;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Orquestracao de confirmacao em duas etapas, entrega e cancelamento de pedidos no PDV.
 *
 * <p>Este Service NAO e @Transactional: a gravacao do pedido ABERTO ocorre na sua propria transacao
 * no repositorio, e a procedure sp_alterar_status_pedido roda em seguida fora de transacao.
 */
@Service
public class PedidoService {

    private static final Logger LOG = LoggerFactory.getLogger(PedidoService.class);

    static final String MENSAGEM_CARRINHO_VAZIO =
            "Adicione ao menos um item ao carrinho antes de confirmar o pedido.";
    static final String MENSAGEM_PAGAMENTO_OBRIGATORIO = "Selecione a forma de pagamento.";
    static final String MENSAGEM_FUNCIONARIO_INVALIDO = "Funcionário não identificado.";
    static final String MENSAGEM_PEDIDO_NAO_ENCONTRADO = "Pedido não encontrado.";
    static final String MENSAGEM_SO_ENTREGA_PRONTO = "Somente pedidos com status PRONTO podem ser entregues.";
    static final String MENSAGEM_SO_CANCELA_EM_PREPARO =
            "Somente pedidos em preparo podem ser cancelados no PDV.";

    private final PedidoRepository repositorio;

    public PedidoService(PedidoRepository repositorio) {
        this.repositorio = repositorio;
    }

    public List<PedidoEmAndamento> listarPedidosEmAndamento() {
        return repositorio.listarPedidosEmAndamento();
    }

    /**
     * Confirma o pedido em duas etapas:
     * (1) grava pedido ABERTO, itens e opcoes em transacao propria;
     * (2) chama sp_alterar_status_pedido para EM_PREPARO fora de transacao.
     * Se a etapa 2 falhar, cancela o pedido ABERTO em nova chamada a procedure e preserva o carrinho.
     */
    public Integer confirmarPedido(CarrinhoPedido carrinho, Integer idFuncionario) {
        if (carrinho == null || carrinho.estaVazio()) {
            throw new RegraNegocioException(MENSAGEM_CARRINHO_VAZIO);
        }
        FormaPagamento formaPagamento = carrinho.getFormaPagamento();
        if (formaPagamento == null) {
            throw new RegraNegocioException(MENSAGEM_PAGAMENTO_OBRIGATORIO);
        }
        if (idFuncionario == null || idFuncionario <= 0) {
            throw new RegraNegocioException(MENSAGEM_FUNCIONARIO_INVALIDO);
        }

        Integer idPedido;
        try {
            idPedido = repositorio.gravarPedidoAberto(
                    carrinho.getIdCliente(),
                    idFuncionario,
                    carrinho.getObservacao(),
                    carrinho.getItens());
        } catch (DataAccessException erro) {
            throw ErrosDoBanco.converter(erro);
        }

        try {
            repositorio.alterarStatusPedido(idPedido, StatusPedido.EM_PREPARO, formaPagamento, idFuncionario);
        } catch (DataAccessException erro) {
            RegraNegocioException erroOriginal = ErrosDoBanco.converter(erro);
            cancelarRascunhoAposFalha(idPedido, idFuncionario);
            throw erroOriginal;
        }

        carrinho.limpar();
        return idPedido;
    }

    public void entregarPedido(Integer idPedido, Integer idFuncionario) {
        validarIdentificadores(idPedido, idFuncionario);
        StatusPedido statusAtual = buscarStatusOuFalhar(idPedido);
        if (statusAtual != StatusPedido.PRONTO) {
            throw new RegraNegocioException(MENSAGEM_SO_ENTREGA_PRONTO);
        }
        executarAlteracaoStatus(idPedido, StatusPedido.ENTREGUE, idFuncionario);
    }

    public void cancelarPedido(Integer idPedido, Integer idFuncionario) {
        validarIdentificadores(idPedido, idFuncionario);
        StatusPedido statusAtual = buscarStatusOuFalhar(idPedido);
        if (statusAtual != StatusPedido.EM_PREPARO) {
            throw new RegraNegocioException(MENSAGEM_SO_CANCELA_EM_PREPARO);
        }
        executarAlteracaoStatus(idPedido, StatusPedido.CANCELADO, idFuncionario);
    }

    private StatusPedido buscarStatusOuFalhar(Integer idPedido) {
        return repositorio.buscarStatusPedido(idPedido)
                .orElseThrow(() -> new RegraNegocioException(MENSAGEM_PEDIDO_NAO_ENCONTRADO));
    }

    private void validarIdentificadores(Integer idPedido, Integer idFuncionario) {
        if (idPedido == null || idPedido <= 0) {
            throw new RegraNegocioException(MENSAGEM_PEDIDO_NAO_ENCONTRADO);
        }
        if (idFuncionario == null || idFuncionario <= 0) {
            throw new RegraNegocioException(MENSAGEM_FUNCIONARIO_INVALIDO);
        }
    }

    private void executarAlteracaoStatus(Integer idPedido, StatusPedido novoStatus, Integer idFuncionario) {
        try {
            repositorio.alterarStatusPedido(idPedido, novoStatus, null, idFuncionario);
        } catch (DataAccessException erro) {
            throw ErrosDoBanco.converter(erro);
        }
    }

    private void cancelarRascunhoAposFalha(Integer idPedido, Integer idFuncionario) {
        try {
            repositorio.alterarStatusPedido(idPedido, StatusPedido.CANCELADO, null, idFuncionario);
        } catch (RuntimeException erroLimpeza) {
            LOG.warn("Falha ao cancelar pedido ABERTO {} apos erro na confirmacao (excecao={}, sqlState={})",
                    idPedido,
                    erroLimpeza.getClass().getSimpleName(),
                    extrairSqlState(erroLimpeza));
        }
    }

    private static String extrairSqlState(Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            if (atual instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
            atual = atual.getCause() == atual ? null : atual.getCause();
        }
        return "desconhecido";
    }
}
