package br.com.snackflow.pedido;

import br.com.snackflow.common.RegraNegocioException;
import br.com.snackflow.funcionario.UsuarioLogado;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Controller web da tela de Ponto de Venda (/pdv). Perfis CAIXA e ADMIN (config/Tela). */
@Controller
@RequestMapping("/pdv")
public class PedidoController {

    static final String CHAVE_CARRINHO_SESSAO = "carrinhoPdv";
    private static final String REDIRECT_PDV = "redirect:/pdv";
    private static final String FLASH_OK = "ok";

    private final PedidoCarrinhoService carrinhoService;
    private final PedidoService pedidoService;

    public PedidoController(PedidoCarrinhoService carrinhoService, PedidoService pedidoService) {
        this.carrinhoService = carrinhoService;
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public String exibirPdv(
            @RequestParam(name = "busca", required = false) String busca,
            @RequestParam(name = "categoria", required = false) String categoria,
            HttpSession sessao,
            Model modelo) {
        CarrinhoPedido carrinho = obterCarrinho(sessao);
        List<ProdutoDisponivel> todosProdutos = carrinhoService.listarProdutosDoCardapio();
        List<String> categorias = todosProdutos.stream()
                .map(ProdutoDisponivel::categoria)
                .distinct()
                .toList();

        String buscaLimpa = busca == null ? "" : busca.trim();
        String categoriaLimpa = categoria == null ? "" : categoria.trim();

        List<ProdutoDisponivel> filtrados = todosProdutos.stream()
                .filter(p -> buscaLimpa.isEmpty()
                        || p.nome().toLowerCase(Locale.ROOT).contains(buscaLimpa.toLowerCase(Locale.ROOT)))
                .filter(p -> categoriaLimpa.isEmpty() || categoriaLimpa.equalsIgnoreCase(p.categoria()))
                .toList();

        FinalizarPedidoForm finalizarForm = new FinalizarPedidoForm();
        finalizarForm.setFormaPagamento(carrinho.getFormaPagamento());
        finalizarForm.setObservacao(carrinho.getObservacao());

        modelo.addAttribute("produtos", filtrados);
        modelo.addAttribute("categorias", categorias);
        modelo.addAttribute("buscaAtual", buscaLimpa);
        modelo.addAttribute("categoriaAtual", categoriaLimpa);
        modelo.addAttribute("opcoesPorProduto", carrinhoService.listarOpcoesAgrupadasPorProduto());
        modelo.addAttribute("carrinho", carrinho);
        modelo.addAttribute("pedidosEmAndamento", pedidoService.listarPedidosEmAndamento());
        modelo.addAttribute("formasPagamento", FormaPagamento.values());
        modelo.addAttribute("finalizarForm", finalizarForm);
        return "pedido/pdv";
    }

    @PostMapping("/itens")
    public String adicionarItem(
            @ModelAttribute AdicionarItemForm form,
            HttpSession sessao,
            RedirectAttributes flash) {
        CarrinhoPedido carrinho = obterCarrinho(sessao);
        int quantidade = form.getQuantidade() == null ? 1 : form.getQuantidade();
        carrinhoService.adicionarItem(
                carrinho,
                form.getIdProduto(),
                quantidade,
                form.getIdsOpcoes(),
                form.getObservacao());
        flash.addFlashAttribute(FLASH_OK, "Item adicionado ao pedido.");
        return REDIRECT_PDV;
    }

    @PostMapping("/itens/{uid}/quantidade")
    public String alterarQuantidade(
            @PathVariable String uid,
            @RequestParam("quantidade") int quantidade,
            HttpSession sessao) {
        CarrinhoPedido carrinho = obterCarrinho(sessao);
        carrinhoService.alterarQuantidade(carrinho, uid, quantidade);
        return REDIRECT_PDV;
    }

    @PostMapping("/itens/{uid}/opcoes")
    public String atualizarOpcoesItem(
            @PathVariable String uid,
            @ModelAttribute AdicionarItemForm form,
            HttpSession sessao,
            RedirectAttributes flash) {
        CarrinhoPedido carrinho = obterCarrinho(sessao);
        carrinhoService.atualizarOpcoesEObservacao(carrinho, uid, form.getIdsOpcoes(), form.getObservacao());
        flash.addFlashAttribute(FLASH_OK, "Opções do item atualizadas.");
        return REDIRECT_PDV;
    }

    @PostMapping("/itens/{uid}/remover")
    public String removerItem(
            @PathVariable String uid,
            HttpSession sessao,
            RedirectAttributes flash) {
        CarrinhoPedido carrinho = obterCarrinho(sessao);
        carrinhoService.removerItem(carrinho, uid);
        flash.addFlashAttribute(FLASH_OK, "Item removido do pedido.");
        return REDIRECT_PDV;
    }

    @PostMapping("/limpar")
    public String limparCarrinho(HttpSession sessao, RedirectAttributes flash) {
        CarrinhoPedido carrinho = obterCarrinho(sessao);
        carrinhoService.limparCarrinho(carrinho);
        flash.addFlashAttribute(FLASH_OK, "Carrinho limpo.");
        return REDIRECT_PDV;
    }

    @PostMapping("/cliente")
    public String vincularCliente(
            @RequestParam(name = "idCliente", required = false) Integer idCliente,
            HttpSession sessao,
            RedirectAttributes flash) {
        CarrinhoPedido carrinho = obterCarrinho(sessao);
        carrinhoService.definirCliente(carrinho, idCliente);
        flash.addFlashAttribute(FLASH_OK,
                idCliente == null ? "Cliente removido do pedido." : "Cliente vinculado ao pedido.");
        return REDIRECT_PDV;
    }

    @PostMapping("/confirmar")
    public String confirmarPedido(
            @ModelAttribute FinalizarPedidoForm form,
            @AuthenticationPrincipal UsuarioLogado usuario,
            HttpSession sessao,
            RedirectAttributes flash) {
        CarrinhoPedido carrinho = obterCarrinho(sessao);
        carrinhoService.definirDadosFinalizacao(carrinho, form.getFormaPagamento(), form.getObservacao());
        Integer idFuncionario = usuario == null ? null : usuario.getId();
        Integer idPedido = pedidoService.confirmarPedido(carrinho, idFuncionario);
        flash.addFlashAttribute(FLASH_OK, "Pedido #" + idPedido + " confirmado e enviado para a cozinha.");
        return REDIRECT_PDV;
    }

    @PostMapping("/pedidos/{id}/entregar")
    public String entregarPedido(
            @PathVariable Integer id,
            @AuthenticationPrincipal UsuarioLogado usuario,
            RedirectAttributes flash) {
        Integer idFuncionario = usuario == null ? null : usuario.getId();
        pedidoService.entregarPedido(id, idFuncionario);
        flash.addFlashAttribute(FLASH_OK, "Pedido #" + id + " marcado como entregue.");
        return REDIRECT_PDV;
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelarPedido(
            @PathVariable Integer id,
            @AuthenticationPrincipal UsuarioLogado usuario,
            RedirectAttributes flash) {
        Integer idFuncionario = usuario == null ? null : usuario.getId();
        pedidoService.cancelarPedido(id, idFuncionario);
        flash.addFlashAttribute(FLASH_OK, "Pedido #" + id + " cancelado.");
        return REDIRECT_PDV;
    }

    @ExceptionHandler(RegraNegocioException.class)
    String tratarRegraNegocio(RegraNegocioException erro, RedirectAttributes flash) {
        flash.addFlashAttribute("erro", erro.getMessage());
        return REDIRECT_PDV;
    }

    private CarrinhoPedido obterCarrinho(HttpSession sessao) {
        Object existente = sessao.getAttribute(CHAVE_CARRINHO_SESSAO);
        if (existente instanceof CarrinhoPedido carrinho) {
            return carrinho;
        }
        CarrinhoPedido novo = new CarrinhoPedido();
        sessao.setAttribute(CHAVE_CARRINHO_SESSAO, novo);
        return novo;
    }
}
