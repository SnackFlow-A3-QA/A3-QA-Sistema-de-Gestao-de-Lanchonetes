package br.com.snackflow.relatorio;

/**
 * Relatórios disponíveis. Os que usam período recebem exatamente 2 parâmetros
 * (início inclusivo, fim exclusivo) sobre a coluna data_hora.
 * Pedidos CANCELADOS aparecem em todos os relatórios de pedidos, em colunas/linhas próprias.
 */
public enum TipoRelatorio {

    FATURAMENTO("Faturamento por dia", true, """
            SELECT DATE(p.data_hora) AS data,
                   COUNT(*) AS pedidos,
                   SUM(CASE WHEN p.status = 'CANCELADO' THEN 1 ELSE 0 END) AS cancelados,
                   COALESCE(SUM(CASE WHEN p.status <> 'CANCELADO' THEN p.valor_total END), 0) AS faturamento,
                   COALESCE(SUM(CASE WHEN p.status = 'CANCELADO' THEN p.valor_total END), 0) AS valor_cancelado
              FROM pedidos p
             WHERE p.data_hora >= ? AND p.data_hora < ?
             GROUP BY DATE(p.data_hora)
             ORDER BY data DESC
            """),

    MAIS_VENDIDOS("Produtos mais vendidos", true, """
            SELECT pr.nome AS produto,
                   c.nome AS categoria,
                   SUM(CASE WHEN p.status <> 'CANCELADO' THEN i.quantidade ELSE 0 END) AS qtd_vendida,
                   COALESCE(SUM(CASE WHEN p.status <> 'CANCELADO' THEN i.subtotal END), 0) AS valor_vendido,
                   SUM(CASE WHEN p.status = 'CANCELADO' THEN i.quantidade ELSE 0 END) AS qtd_cancelada,
                   COALESCE(SUM(CASE WHEN p.status = 'CANCELADO' THEN i.subtotal END), 0) AS valor_cancelado
              FROM itens_pedido i
              JOIN pedidos p ON p.id_pedido = i.id_pedido
              JOIN produtos pr ON pr.id_produto = i.id_produto
              JOIN categorias c ON c.id_categoria = pr.id_categoria
             WHERE p.data_hora >= ? AND p.data_hora < ?
             GROUP BY pr.id_produto, pr.nome, c.nome
             ORDER BY qtd_vendida DESC, pr.nome
            """),

    PEDIDOS_POR_STATUS("Pedidos por status", true, """
            SELECT p.status AS status,
                   COUNT(*) AS pedidos,
                   COALESCE(SUM(p.valor_total), 0) AS valor_total
              FROM pedidos p
             WHERE p.data_hora >= ? AND p.data_hora < ?
             GROUP BY p.status
             ORDER BY pedidos DESC
            """),

    PEDIDOS_POR_CLIENTE("Pedidos por cliente", true, """
            SELECT COALESCE(c.nome, 'Balcão (sem cadastro)') AS cliente,
                   COUNT(*) AS pedidos,
                   SUM(CASE WHEN p.status = 'CANCELADO' THEN 1 ELSE 0 END) AS cancelados,
                   COALESCE(SUM(CASE WHEN p.status <> 'CANCELADO' THEN p.valor_total END), 0) AS total_gasto
              FROM pedidos p
              LEFT JOIN clientes c ON c.id_cliente = p.id_cliente
             WHERE p.data_hora >= ? AND p.data_hora < ?
             GROUP BY c.id_cliente, c.nome
             ORDER BY total_gasto DESC
            """),

    PERDAS_E_AJUSTES("Perdas e ajustes de estoque", true, """
            SELECT m.data_hora AS data_hora,
                   pr.nome AS produto,
                   m.motivo AS motivo,
                   m.tipo AS tipo,
                   m.quantidade AS quantidade,
                   f.nome AS funcionario,
                   m.observacao AS observacao
              FROM movimentacoes_estoque m
              JOIN produtos pr ON pr.id_produto = m.id_produto
              LEFT JOIN funcionarios f ON f.id_funcionario = m.id_funcionario
             WHERE m.motivo IN ('PERDA', 'AJUSTE')
               AND m.data_hora >= ? AND m.data_hora < ?
             ORDER BY m.data_hora DESC
            """),

    POSICAO_ESTOQUE("Posição de estoque", false, """
            SELECT nome AS produto,
                   categoria,
                   saldo,
                   estoque_minimo,
                   CASE WHEN estoque_baixo = 1 THEN 'BAIXO' ELSE 'OK' END AS situacao
              FROM vw_estoque_atual
             ORDER BY estoque_baixo DESC, nome
            """);

    private final String titulo;
    private final boolean usaPeriodo;
    private final String sql;

    TipoRelatorio(String titulo, boolean usaPeriodo, String sql) {
        this.titulo = titulo;
        this.usaPeriodo = usaPeriodo;
        this.sql = sql;
    }

    public String getTitulo() { return titulo; }
    public boolean isUsaPeriodo() { return usaPeriodo; }
    public String getSql() { return sql; }
}
