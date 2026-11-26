package br.com.snackflow.config;

import br.com.snackflow.funcionario.Perfil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Monta o menu lateral filtrado pelo perfil. Só entram telas que {@link Tela#apareceNoMenu()} (as que
 * têm seção) e que o perfil pode acessar; rotas sem seção, como {@code /clientes}, ficam fora do menu
 * mas continuam protegidas por perfil. Seções sem itens visíveis não aparecem.
 */
@Service
public class MenuService {

    public List<MenuSecao> montar(Perfil perfil, String caminhoAtual) {
        List<MenuSecao> secoes = new ArrayList<>();
        for (Tela.Secao secao : Tela.Secao.values()) {
            List<MenuItem> itens = new ArrayList<>();
            for (Tela tela : Tela.values()) {
                if (tela.apareceNoMenu() && tela.secao() == secao && tela.permitidaPara(perfil)) {
                    itens.add(new MenuItem(tela.rota(), tela.rotulo(), tela.icone(),
                            estaAtivo(tela.rota(), caminhoAtual)));
                }
            }
            if (!itens.isEmpty()) {
                secoes.add(new MenuSecao(secao.titulo(), List.copyOf(itens)));
            }
        }
        return List.copyOf(secoes);
    }

    /** Ativo na própria rota e em sub-rotas (/produtos/novo), mas não em /produtosx. */
    private boolean estaAtivo(String rota, String caminhoAtual) {
        return caminhoAtual != null && (caminhoAtual.equals(rota) || caminhoAtual.startsWith(rota + "/"));
    }
}
