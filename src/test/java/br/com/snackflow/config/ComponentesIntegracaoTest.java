package br.com.snackflow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.snackflow.TestcontainersConfiguration;
import br.com.snackflow.funcionario.Perfil;
import br.com.snackflow.funcionario.UsuarioLogado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Renderiza os componentes compartilhados (campos, tabela, alerta, badges, botão) em uma tela de
 * teste. Garante que um módulo que siga o guia consegue usá-los. Não grava nada no banco.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ComponentesIntegracaoTest {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;
    private final UsuarioLogado admin = new UsuarioLogado(1, "Carlos Silva", "admin", "hash", Perfil.ADMIN, true);

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    private String renderizarFormularioVazio() throws Exception {
        return mvc.perform(get("/teste/formulario").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void deveRenderizarCamposComRotuloObrigatorioEPlaceholder() throws Exception {
        String html = renderizarFormularioVazio();

        assertThat(html).contains("Nome do produto", "Ex.: X-Burger", "name=\"nome\"", "name=\"descricao\"")
                .contains("Selecione", "Lanches", "Bebidas", "name=\"ativo\"", "Salvar")
                .doesNotContain("sf-invalido");
    }

    @Test
    void deveRenderizarTabelaAlertaEBadges() throws Exception {
        String html = renderizarFormularioVazio();

        assertThat(html).contains("sf-tabela", "Preço", "X-Burger", "Atenção de exemplo")
                .contains("Estoque baixo", "sf-badge-aviso", "Pronto", "sf-badge-sucesso");
    }

    @Test
    void deveMarcarCampoInvalidoComBordaVermelhaEMensagem() throws Exception {
        String html = mvc.perform(post("/teste/formulario").with(user(admin)).with(csrf()).param("nome", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("sf-invalido", "aria-invalid=\"true\"", "sf-erro-campo");
    }

    @Test
    void deveManterValorDigitadoQuandoHaErroEmOutroCampo() throws Exception {
        String html = mvc.perform(post("/teste/formulario").with(user(admin)).with(csrf())
                        .param("nome", "").param("descricao", "Texto digitado"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Texto digitado");
    }
}
