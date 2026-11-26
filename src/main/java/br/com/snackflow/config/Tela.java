package br.com.snackflow.config;

import static br.com.snackflow.funcionario.Perfil.ADMIN;
import static br.com.snackflow.funcionario.Perfil.CAIXA;
import static br.com.snackflow.funcionario.Perfil.COZINHA;
import static br.com.snackflow.funcionario.Perfil.ESTOQUE;

import br.com.snackflow.funcionario.Perfil;
import java.util.EnumSet;
import java.util.Set;

/**
 * FONTE ÚNICA das rotas protegidas: quem pode acessar cada uma e como aparece no menu.
 * Alimenta o {@link MenuService} (menu lateral) e o {@link SegurancaConfig} (403 por perfil),
 * então menu e segurança nunca divergem.
 *
 * <p>Para um módulo novo, adicione uma linha aqui (e só aqui). Rotas fora do menu (por exemplo,
 * /clientes, usada pelo PDV) usam {@code secao = null}.
 */
enum Tela {
    PDV("/pdv", "Ponto de venda", "shopping-cart", Secao.OPERACAO, EnumSet.of(CAIXA, ADMIN)),
    COZINHA_KDS("/cozinha", "Cozinha (KDS)", "chef-hat", Secao.OPERACAO, EnumSet.of(COZINHA, ADMIN)),
    ESTOQUE_TELA("/estoque", "Estoque", "package", Secao.OPERACAO, EnumSet.of(ESTOQUE, ADMIN)),
    PRODUTOS("/produtos", "Cardápio e produtos", "utensils", Secao.ADMINISTRACAO, EnumSet.of(ADMIN)),
    FUNCIONARIOS("/funcionarios", "Funcionários", "users", Secao.ADMINISTRACAO, EnumSet.of(ADMIN)),
    RELATORIOS("/relatorios", "Relatórios", "bar-chart-3", Secao.ADMINISTRACAO, EnumSet.of(ADMIN)),
    CLIENTES("/clientes", "Clientes", "user-round", null, EnumSet.of(CAIXA, ADMIN));

    /** Seções do menu lateral, na ordem de exibição. */
    enum Secao {
        OPERACAO("OPERAÇÃO"),
        ADMINISTRACAO("ADMINISTRAÇÃO");

        private final String titulo;

        Secao(String titulo) {
            this.titulo = titulo;
        }

        String titulo() {
            return titulo;
        }
    }

    private final String rota;
    private final String rotulo;
    private final String icone;
    private final Secao secao;
    private final Set<Perfil> perfis;

    Tela(String rota, String rotulo, String icone, Secao secao, Set<Perfil> perfis) {
        this.rota = rota;
        this.rotulo = rotulo;
        this.icone = icone;
        this.secao = secao;
        this.perfis = perfis;
    }

    String rota() {
        return rota;
    }

    String rotulo() {
        return rotulo;
    }

    String icone() {
        return icone;
    }

    Secao secao() {
        return secao;
    }

    boolean apareceNoMenu() {
        return secao != null;
    }

    boolean permitidaPara(Perfil perfil) {
        return perfis.contains(perfil);
    }

    /** Nomes dos perfis, no formato aceito por hasAnyRole (sem o prefixo ROLE_). */
    String[] papeis() {
        return perfis.stream().map(Perfil::name).toArray(String[]::new);
    }
}
