package br.com.snackflow.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ClienteApiControllerTest {

    @Mock
    private ClienteService servico;

    @InjectMocks
    private ClienteApiController controller;

    @Test
    void deveDelegarABuscaAoService() {
        List<ClienteResposta> esperado = List.of(new ClienteResposta(1, "Ana", null, null));
        when(servico.buscar("an")).thenReturn(esperado);

        assertThat(controller.buscar("an")).isSameAs(esperado);
    }

    @Test
    void deveResponder201ComOClienteCriado() {
        ClienteRapidoForm form = new ClienteRapidoForm("Ana", "48987654321", null);
        ClienteResposta criado = new ClienteResposta(7, "Ana", null, "(48) 98765-4321");
        when(servico.cadastrarRapido(form)).thenReturn(criado);

        ResponseEntity<ClienteResposta> resposta = controller.cadastrarRapido(form);

        assertThat(resposta.getStatusCode().value()).isEqualTo(201);
        assertThat(resposta.getBody()).isEqualTo(criado);
    }

    @Test
    void deveDelegarAEdicaoAoService() {
        ClienteRapidoForm form = new ClienteRapidoForm("Ana", "48987654321", null);
        ClienteResposta editado = new ClienteResposta(5, "Ana", null, "(48) 98765-4321");
        when(servico.atualizar(5, form)).thenReturn(editado);

        assertThat(controller.atualizar(5, form)).isEqualTo(editado);
    }

    @Test
    void deveResponder204AoInativar() {
        ResponseEntity<Void> resposta = controller.inativar(5);

        assertThat(resposta.getStatusCode().value()).isEqualTo(204);
        org.mockito.Mockito.verify(servico).inativar(5);
    }
}
