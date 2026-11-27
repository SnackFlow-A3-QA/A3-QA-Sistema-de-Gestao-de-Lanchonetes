# Guia do módulo

Como desenvolver o seu módulo reaproveitando a fundação do SnackFlow, sem criar infraestrutura nem padrões próprios.
Leia junto com o *Documento de Planejamento* (`docs/`), que continua sendo a fonte de decisões. Se algo aqui
conflitar com o planejamento, vale o planejamento e o `database/lanchonete.sql`.

**Índice:** 1 Fundação · 2 Pacotes · 3 Rotas · 4 Banco · 5 Erros · 6 Layout e componentes · 7 Testes · 8 Executar · 9 Arquivos compartilhados · 10 Checklist · **11 Mapa dos módulos e regras** · **12 Armadilhas conhecidas** · **13 Como pedir seu módulo ao Claude** · **14 Contrato PDV ↔ clientes (decidido)**

## 1. O que a fundação já entrega

| Item | Onde | Observação |
|---|---|---|
| Login, logout (POST), CSRF, BCrypt | `config/SegurancaConfig`, `funcionario/*` | Autentica pelos funcionários ativos do banco |
| Acesso por perfil (403) e menu lateral | `config/Tela` | **Fonte única**: uma linha por rota |
| Dados do layout em todo modelo | `config/LayoutAdvice` | `usuarioNome`, `usuarioPerfil`, `menuSecoes` |
| Layout, header, menu, toasts, modal | `templates/fragments/layout.html` | Você só escreve o conteúdo da tela |
| Componentes (campos, botões, tabela, badges...) | `templates/fragments/componentes.html` + `static/css/snackflow.css` | Veja a seção 6 |
| Erros de regra e do banco | `common/RegraNegocioException`, `common/ErrosDoBanco`, `common/TratadorGlobalDeExcecoes` (telas), `common/TratadorJsonDeExcecoes` (endpoints JSON) | Veja a seção 5 |
| Páginas 403, 404 e 5xx | `templates/error/` | Automáticas |
| MySQL local, banco de teste | `docker-compose.yml`, `TestcontainersConfiguration` | Veja a seção 8 |

Usuários de exemplo (senha `snackflow123`, apenas desenvolvimento): `admin`, `ana.caixa`, `marcos.cozinha`, `julia.estoque` e `roberto.antigo` (CAIXA **inativo**, útil para testar a recusa de login).

## 2. Onde criar cada classe

Pacote raiz `br.com.snackflow`. Um pacote por módulo; trabalhe só no seu.

```
src/main/java/br/com/snackflow/<modulo>/
    Produto.java              Entidade JPA (ou record de leitura, se o acesso for por JdbcTemplate)
    ProdutoRepository.java    Acesso a dados (JPA ou JdbcTemplate)
    ProdutoService.java       REGRAS DE NEGÓCIO. Lança RegraNegocioException
    ProdutoController.java    Só traduz HTTP para o Service
    ProdutoForm.java          Dados do formulário + Bean Validation
src/main/resources/templates/<modulo>/    lista.html, form.html ...
src/test/java/br/com/snackflow/<modulo>/  Testes, no MESMO pacote da classe testada
```

Módulos: `funcionario`, `cliente`, `produto`, `estoque`, `pedido`, `cozinha`, `relatorio`.
Um módulo não importa classes de outro módulo. Se precisar de dados de outro módulo, leia pela view/tabela do banco no seu próprio Repository; se precisar de um contrato HTTP (ex.: PDV usando `/clientes`), siga o contrato da seção 14. Exceção: `pedido` e `cozinha` foram planejados juntos e podem compartilhar o acesso à procedure.

Regras de código (do planejamento): comentários e nomes em português sem acento (`ProdutoService`,
`deveRejeitarPrecoNegativo`); mensagens ao usuário em português com acento; sem Lombok; injeção por construtor
(nunca `@Autowired` em campo); `BigDecimal` para dinheiro; ids como `Integer`.

## 3. Registrar a rota do módulo (um passo, uma linha)

Abra `config/Tela.java` e confira se a sua rota já existe (`/pdv`, `/cozinha`, `/estoque`, `/produtos`,
`/funcionarios`, `/relatorios`, `/clientes`). As rotas do planejamento já estão lá, com os perfis corretos.
**Rota nova** (fora do planejamento) exige uma linha em `Tela`, registrada na documentação. Isso atualiza,
ao mesmo tempo, o menu e a regra de 403. Não crie `requestMatchers` em outro lugar.

Sub-rotas (`/produtos/novo`, `/produtos/5/editar`) herdam a permissão da rota principal.

## 4. Acessar o banco pelas camadas

| Situação | Como |
|---|---|
| Cadastro simples (categorias, produtos, ingredientes, clientes, funcionários) | JPA: `Entity` + `JpaRepository` |
| Views, relatórios, procedure, escrita de pedido/itens/opções | `JdbcTemplate` devolvendo `record`s |

```java
// Entidade: ENUM como String, TINYINT(1) como boolean, DECIMAL como BigDecimal.
// Colunas controladas por trigger/procedure são SOMENTE LEITURA para a aplicação:
@Column(name = "saldo_atual", insertable = false, updatable = false)
private BigDecimal saldoAtual;
```

```java
// Repository com JdbcTemplate (leitura de view): record + RowMapper, sem SQL espalhado pelo Controller
@Repository
class DisponibilidadeRepository {
    record Disponibilidade(Integer idProduto, String nome, int disponivel) { }

    private final JdbcTemplate jdbc;

    DisponibilidadeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Disponibilidade> listar() {
        return jdbc.query("SELECT id_produto, nome, disponivel FROM vw_disponibilidade_produtos WHERE ativo = 1",
                (rs, n) -> new Disponibilidade(rs.getInt("id_produto"), rs.getString("nome"), rs.getInt("disponivel")));
    }
}
```

Procedure `sp_alterar_status_pedido` (**nunca** dentro de `@Transactional`, ela abre e fecha a própria transação):

```java
void alterarStatus(Integer idPedido, String status, String forma, UsuarioLogado usuario) {
    try {
        jdbc.update("CALL sp_alterar_status_pedido(?, ?, ?, ?)", idPedido, status, forma, usuario.getId());
    } catch (DataAccessException erro) {
        throw ErrosDoBanco.converter(erro);   // devolve a mensagem amigável do trigger
    }
}
```

Funcionário logado: no Controller, `@AuthenticationPrincipal UsuarioLogado usuario` (`getId()`, `getNome()`, `getPerfil()`).
Use `usuario.getId()` em `pedidos.id_funcionario`, nas movimentações e na procedure.

O banco nunca é alterado pelo Hibernate (`ddl-auto=none`). Mudança em `database/lanchonete.sql` passa por PR e deve ser registrada na documentação.

## 5. Validar e tratar erros

Duas camadas, cada uma com sua função:

1. **Formato do formulário** (campo vazio, tamanho, número): Bean Validation no `Form`
   (`@NotBlank`, `@NotNull`, `@DecimalMin`...). O Controller recebe `BindingResult` e reexibe o formulário.
2. **Regra de negócio** (nome duplicado, estoque insuficiente, REMOVER só para ingrediente da receita): no
   **Service**, lançando `RegraNegocioException("mensagem segura em português")`.
   Erros do banco (`DataAccessException`, trigger com SQLSTATE 45000) viram `RegraNegocioException` por
   `ErrosDoBanco.converter(erro)`. A mensagem técnica do banco nunca vai ao usuário, exceto a do trigger, que já é amigável.

Padrão do Controller (post-redirect-get):

```java
@Controller
@RequestMapping("/produtos")
class ProdutoController {

    private static final String TELA_FORM = "produto/form";
    private final ProdutoService servico;

    ProdutoController(ProdutoService servico) {
        this.servico = servico;
    }

    @GetMapping
    String listar(Model modelo) {
        modelo.addAttribute("produtos", servico.listar());
        return "produto/lista";
    }

    @PostMapping("/novo")
    String cadastrar(@Valid @ModelAttribute("form") ProdutoForm form, BindingResult erros,
                     Model modelo, RedirectAttributes flash) {
        if (erros.hasErrors()) {
            return TELA_FORM;                                  // erros de formato
        }
        try {
            servico.cadastrar(form);
        } catch (RegraNegocioException erro) {
            modelo.addAttribute("erro", erro.getMessage());    // vira toast vermelho
            return TELA_FORM;
        }
        flash.addFlashAttribute("ok", "Produto cadastrado.");  // vira toast verde após o redirect
        return "redirect:/produtos";
    }
}
```

- `ok` e `erro` no modelo ou no flash viram toast automaticamente (não precisa de HTML na tela).
- Se o Controller esquecer de capturar uma `RegraNegocioException`/`DataAccessException`, o
  `TratadorGlobalDeExcecoes` volta para a tela anterior com a mensagem no toast.
- **Endpoints JSON/`fetch`** (busca de clientes, cadastro rápido em modal, atualização da cozinha): o
  `TratadorGlobalDeExcecoes` **redireciona** (serve só para telas). Em classes `@RestController` quem responde é o
  `TratadorJsonDeExcecoes`, com status HTTP e corpo JSON; você não precisa de `try/catch` para isso (formato na seção 12,
  item 5). Use `@RestController`: um método `@Controller` com `@ResponseBody` continua no tratador de telas.
- Acesso negado (403) e rota inexistente (404) têm páginas próprias; erro inesperado mostra a página 5xx sem detalhes.

## 6. Usar o layout e os componentes

### Estrutura de uma tela

```html
<!DOCTYPE html>
<html lang="pt-BR" xmlns:th="http://www.thymeleaf.org"
      th:replace="~{fragments/layout :: pagina('Produtos', 'Cardápio e produtos', ~{::conteudo})}">
<body>
<section th:fragment="conteudo">
    <!-- só o conteúdo da tela: cabeçalho, menu, toasts e modal vêm do layout -->
</section>
</body>
</html>
```

Parâmetros: texto do breadcrumb/`<title>`, título da página (h1) e o fragmento `conteudo`.
Scripts específicos da tela: coloque `<script>` dentro do próprio `conteudo`.

### Componentes (`fragments/componentes`)

Os exemplos de uso de cada um estão no comentário no topo de `componentes.html`. Resumo:

| Componente | Chamada |
|---|---|
| Estado vazio | `estadoVazio('inbox', 'Nenhum produto', 'Cadastre o primeiro item.')` |
| Badge do pedido | `badgeStatus(${pedido.status})` (String ou enum) |
| Badge livre | `badge('Estoque baixo', 'aviso', 'alert-triangle')` (sucesso, aviso, perigo, info, neutro) |
| Alerta na página | `alerta('perigo', ${mensagem})` |
| Botão / botão-link | `botao('Salvar', 'save', 'primario')` / `linkBotao('/produtos', 'Voltar', 'arrow-left', 'contorno')` |
| Campos | `campoTexto(rotulo, campo, obrigatorio, tipo, placeholder)`, `campoTextarea`, `campoSelect`, `campoCheckbox` (dentro de `<form th:object="${form}">`) |
| Cartão | `cartao('Título', ~{::corpo})` |
| Tabela | `tabela(${ {'Nome','Preço'} }, ~{::linhas})`, com `<tbody th:fragment="linhas">` |
| Spinner | `spinner('Carregando…')` |

Quando precisar de atributos especiais (por exemplo `type="button"`, `onclick`), use as classes direto:
`sf-btn sf-btn-primario|secundario|contorno|perigo` (+ `sf-btn-compacto`, `sf-btn-grande`), `sf-input`,
`sf-rotulo`, `sf-tabela`, `sf-badge sf-badge-*`. **Não crie CSS de componente no módulo**; se faltar algo,
ajuste o componente compartilhado (seção 9) em vez de criar CSS no módulo.

Regras do Design System: rótulo acima do campo, obrigatório com `*`, erro com borda vermelha e mensagem,
nunca estado só por cor (sempre texto ou ícone), ícones Lucide (`<i data-lucide="nome"></i>`).

### Confirmação, carregamento e toast sem JavaScript próprio

- **Confirmar ação crítica** (cancelar pedido, registrar perda): adicione atributos ao botão:
  `<button type="submit" data-confirmar-mensagem="Os ingredientes voltarão ao estoque."
  data-confirmar-titulo="Cancelar pedido?" data-confirmar-rotulo="Confirmar cancelamento">`.
  Funciona também em links (`<a>`). Use `data-confirmar-tipo="padrao"` para botão laranja em vez de vermelho.
- **Carregamento**: botões de envio mostram spinner e ficam desabilitados ao enviar. Texto opcional:
  `data-texto-carregando="Salvando…"`. Para desligar no formulário: `data-sem-carregamento`.
- **Toast por JavaScript** (respostas de `fetch`): `SnackFlow.toast('Título', 'Mensagem', 'sucesso|erro|aviso|info')`.
- **Requisições `fetch` POST**: envie o token CSRF (`<meta name="_csrf">` e `<meta name="_csrf_header">` já estão na página).
- Ícones criados dinamicamente: chame `SnackFlow.criarIcones()` depois de inserir o HTML.

## 7. Escrever testes

Regras: classe termina em `Test`; mesmo pacote da classe testada; Arrange/Act/Assert; nome como
`deveRejeitarPrecoNegativo`; todo método público de Service tem um teste de sucesso e um de falha;
**Todo código novo nasce com teste.** O JaCoCo falha o `./mvnw verify` abaixo de 80% de linhas.

| Tipo | Quando | Modelo |
|---|---|---|
| Unitário de Service | Sempre (é a base dos 80%) | JUnit 5 + Mockito + AssertJ, repositório simulado |
| Controller | Fluxo de sucesso e de erro | Chamada direta ao método com `BindingResult`/`RedirectAttributes` simulados |
| Web (rotas, perfil, 403, renderização) | Telas | `MockMvcBuilders.webAppContextSetup(...).apply(springSecurity())`, como em `SegurancaELayoutIntegracaoTest` |
| Integração com banco real | Regras do trigger/procedure/view | `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` |

```java
@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock private ProdutoRepository repositorio;
    @InjectMocks private ProdutoService servico;

    @Test
    void deveRejeitarPrecoNegativo() {
        ProdutoForm form = new ProdutoForm();
        form.setPreco(new BigDecimal("-1"));

        assertThatThrownBy(() -> servico.cadastrar(form))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O preço não pode ser negativo.");
    }
}
```

Testes de integração:

- O banco de teste já nasce com os dados de exemplo do script. **Não dependa deles**: crie dados próprios com
  nomes únicos (`"produto-" + System.nanoTime()`) ou limpe com `@Sql`.
- **Quem chama `sp_alterar_status_pedido` não pode ser `@Transactional`** (a procedure faz commit).
  Sem `@Transactional`, limpe o que criou.
- Teste que só grava com JPA pode ser `@Transactional` (é desfeito ao final), como `SegurancaELayoutIntegracaoTest`.
- Para exercitar uma tela real, autentique com `.with(user(new UsuarioLogado(1, "Nome", "login", "hash", Perfil.ADMIN, true)))`
  e `.with(csrf())` nos POST.
- **Controller de exemplo aninhado na classe de teste** (`@RestController` dentro do `*Test`) não é varrido pelo Spring Boot: sem registro, a rota responde 404. Importe-o (`@Import({TestcontainersConfiguration.class, MeuTest.Exemplo.class})`) e confirme o mapeamento via `RequestMappingHandlerMapping` antes das requisições, como em `TratadorJsonBancoIntegracaoTest`. Prefira isso a enfraquecer a segurança ou criar a rota em produção.
- Há telas de exemplo só de teste (`/teste/layout`, `/teste/formulario`, em `src/test`) mostrando o layout e os componentes.

## 8. Executar o projeto

Pré-requisitos: JDK 21 e **Docker aberto**.

```bash
docker compose up -d          # MySQL local com database/lanchonete.sql (aguarde ficar "healthy": docker compose ps)
./mvnw spring-boot:run        # http://localhost:8080
./mvnw verify                 # testes + cobertura (Docker aberto); relatório em target/site/jacoco/index.html
docker compose down -v        # apaga o banco; o próximo "up" recria do zero
```

Use sempre o **Maven Wrapper** (`./mvnw`; no Windows PowerShell também `./mvnw`). O comando `mvn` só funciona se o Maven estiver
instalado e no `PATH`: no PowerShell do responsável, `mvn spring-boot:run` falhou com "termo 'mvn' não reconhecido" e
`./mvnw spring-boot:run` subiu a aplicação.

Variáveis de ambiente (opcionais; o padrão aponta para o `docker-compose.yml`):
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `THYMELEAF_CACHE=false` (recarrega templates sem reiniciar, em desenvolvimento).
Segredos nunca vão para o repositório.

## 9. Arquivos compartilhados

Altere só de propósito, em commit ou PR próprio, e descreva a mudança na documentação (`docs/PROGRESSO-FUNDACAO.md`). A decisão é do responsável pelo projeto.

| Arquivo ou pasta | Motivo |
|---|---|
| `database/lanchonete.sql` | Fonte de verdade do banco; afeta todos |
| `pom.xml`, `.github/workflows/ci.yml`, `docker-compose.yml` | Build, CI, Sonar e ambiente |
| `src/main/resources/application.properties` | Configuração de ambiente |
| `config/*` (segurança, `Tela`, menu, `LayoutAdvice`, login) | Acesso e menu de todos os módulos |
| `funcionario/Funcionario`, `UsuarioLogado`, `Perfil`, `FuncionarioUserDetailsService` | Autenticação (o CRUD de funcionários é o módulo `funcionario`, mas a autenticação não pode quebrar) |
| `common/*` | Exceções e tratamento de erros |
| `templates/fragments/*`, `templates/error/*`, `templates/login.html` | Layout e componentes |
| `static/css/snackflow.css`, `static/js/*` | Estilo e comportamento compartilhados |
| `src/test/.../TestcontainersConfiguration` | Contêiner único de teste |

O que **é seu**: o pacote do módulo (`src/main/java/.../<modulo>`), `templates/<modulo>/` e os testes do módulo.

## 10. Checklist antes do PR

- [ ] Só alterei o meu módulo (ou avisei sobre arquivo compartilhado).
- [ ] Regras de negócio no Service; Controller só traduz HTTP.
- [ ] Mensagens de erro em português; nenhum erro técnico do banco chega ao usuário.
- [ ] Tela usa `fragments/layout` e os componentes; sem CSS ou JS duplicado.
- [ ] Perfil correto exigido (rota em `Tela`) e testado (403).
- [ ] Testes de sucesso e de falha; integração nas regras críticas do banco.
- [ ] `./mvnw verify` verde localmente (Docker aberto), cobertura ≥ 80%.
- [ ] As regras da seção 7 do planejamento (resumidas na seção 11 deste guia) estão implementadas e o perfil correto é exigido.
- [ ] A tela tem os estados de sucesso, erro e vazio, conforme o Design System.
- [ ] Commits pequenos, Conventional Commits, na minha conta; PR com `Closes #N`, CI e Sonar aprovados.

## 11. Mapa dos módulos e regras

Resumo da seção 7 do planejamento (que prevalece em caso de dúvida). A coluna *Dono* guarda a alocação original do planejamento (números 1 a 4) apenas como organização pessoal dos módulos: não são pessoas nem aprovações exigidas.
Ordem sugerida pelo cronograma: **produtos primeiro** (fecha o issue do professor), depois estoque, PDV, clientes e cozinha.

| Pacote | Rota (perfis) | Dono | Regras-chave | Banco |
|---|---|---|---|---|
| `produto` | `/produtos` (ADMIN) | 3 | Nome único; preço não negativo; ficha técnica com ingredientes ativos; opção ADICIONAL exige quantidade e REMOVER só para ingrediente da receita (sem preço); produto sem receita é sinalizado como não vendável; inativar preserva histórico; margem em R$ e % e alerta abaixo do custo; simulador `custo / (1 - margem)` na aplicação | `produtos`, `produto_ingredientes`, `produto_opcoes`, `categorias`, `vw_produtos_custo` |
| `estoque` | `/estoque` (ESTOQUE, ADMIN) | 4 | Situação: Sem estoque (zero), Estoque baixo (menor que o mínimo), Normal; compra exige custo e aceita kg/L convertendo para G/ML; perda e ajuste exigem justificativa; saldo insuficiente recusado; extrato imutável com filtro por ingrediente; custo médio ponderado (50 a 4,00 + 50 a 5,00 = 4,50) | `ingredientes`, `movimentacoes_estoque`, `vw_estoque_ingredientes` |
| `pedido` (PDV) | `/pdv` (CAIXA, ADMIN) | 1 | Carrinho na sessão HTTP; total = (preço + adicionais) × quantidade; produto indisponível não entra; pagamento obrigatório; **confirmação em duas etapas** (1: transação grava ABERTO + itens + opções e faz commit; 2: fora de transação, `sp_alterar_status_pedido(id,'EM_PREPARO',forma,idFuncionario)`; se falhar, a procedure cancela o ABERTO, o carrinho é mantido e a mensagem do banco aparece); entregar só em PRONTO; cancelar só em EM_PREPARO com modal avisando a devolução de ingredientes | `pedidos`, `itens_pedido`, `itens_pedido_opcoes`, `vw_disponibilidade_produtos`, procedure |
| `cozinha` | `/cozinha` (COZINHA, ADMIN) | 1 | Fila de `vw_cozinha` agrupada por pedido, do mais antigo ao mais novo, primeiro em destaque; adicionais em âmbar, remoções (SEM) em vermelho; "Marcar como PRONTO" chama a procedure com o funcionário logado; atualiza a cada 15 s; estado vazio; COZINHA tentando CANCELADO é recusada pelo banco | `vw_cozinha`, procedure |
| `cliente` | `/clientes` (CAIXA, ADMIN) | 2 | Busca por nome ou CPF; cadastro rápido em modal sem sair do PDV; CPF opcional, mas válido (mesma regra de `fn_cpf_valido`) e único (também na edição); normalizar nome e telefone; inativar e editar | `clientes` |
| `funcionario` | `/funcionarios` (ADMIN) | 2 | Senha gravada só como hash BCrypt (`PasswordEncoder`); login único; redefinir senha; ativar/inativar; o usuário logado não pode se inativar | `funcionarios` |
| `relatorio` | `/relatorios` (ADMIN) | 3 (vendas, controller e template) e 4 (service de estoque) | Período com início e fim inclusivos na tela (a consulta usa fim + 1 dia, exclusivo); sem datas, últimos 30 dias; período invertido rejeitado; CANCELADO fora do faturamento, em coluna própria; lucro = valor do pedido − soma(quantidade × custo das saídas VENDA); ticket médio; ranking de produtos; reposição com sugestão `2 × mínimo − saldo`. A alocação original separava o service de estoque (4) do restante do relatório (3); como organização, pode ser feito em etapas separadas, cada uma com seus testes | `pedidos`, `movimentacoes_estoque`, views |

Notas por módulo:

- **`funcionario`**: a fundação já tem `Funcionario`, `FuncionarioRepository`, `UsuarioLogado`, `Perfil` e o `UserDetailsService`. O CRUD estende esses arquivos; `Funcionario` hoje só tem `setAtivo`, então novos setters/construtores são uma mudança de arquivo compartilhado (registre na documentação) e **não podem quebrar** os testes de autenticação existentes.
- **`pedido`**: escrita de pedido, itens e opções com `JdbcTemplate` (os triggers alteram linhas depois do insert; o cache do Hibernate ficaria obsoleto). Leitura de views também por `JdbcTemplate`, com `record`.
- **`estoque`**: nunca grave `saldo_atual` ou `custo_atual`; tudo entra por `INSERT` em `movimentacoes_estoque` (o trigger calcula). Correção de erro é um AJUSTE, não um UPDATE.
- **Corte de escopo** (se o prazo apertar, nesta ordem): simulador de preço, exportação CSV, página dedicada de clientes, relatório de perdas e ajustes, teste de concorrência.

## 12. Armadilhas conhecidas

Problemas reais encontrados ao validar a fundação ou exigidos pelo banco. Leia antes de começar.

1. **Procedure e transação**: `sp_alterar_status_pedido` faz o próprio `START TRANSACTION/COMMIT`. Chamada dentro de `@Transactional`, ela confirma a transação pendente. Teste de integração que a chama também não pode ser `@Transactional`.
2. **Cache do Hibernate**: não leia pedido/itens por JPA depois de a procedure ou os triggers alterarem linhas; use `JdbcTemplate`.
3. **Triggers são rígidos**: pedido fora de ABERTO não aceita itens nem mudança de campos; status não pula nem volta etapa; `valor_total` não é editável; movimentação não sofre UPDATE/DELETE; pedido não se exclui (use CANCELADO). Trate a mensagem do trigger com `ErrosDoBanco.converter`.
4. **Carrinho na sessão**: o objeto guardado em `HttpSession` deve ser `Serializable`, e o Service não pode guardar estado do usuário em campo (é singleton). O `UsuarioLogado` já é serializável.
5. **Endpoints JSON (`fetch`)**: classes `@RestController` passam pelo `TratadorJsonDeExcecoes`
   (`common`, escopo `@RestController`, prioridade máxima), que **não redireciona**. Telas (`@Controller`) seguem com o
   redirecionamento e o toast. Formato do corpo; **nas respostas produzidas por este tratador** a chave `erro` está sempre presente, em português e segura:

   | Situação | Status | Corpo |
   |---|---|---|
   | `RegraNegocioException` | 422 | `{"erro": "CPF inválido."}` |
   | Erro de banco com mensagem de trigger/procedure (SQLSTATE 45000) | 422 | `{"erro": "<mensagem do trigger>"}` |
   | Bean Validation em `@Valid @RequestBody` (ou `@ModelAttribute`) | 400 | `{"erro": "Dados inválidos.", "campos": {"nome": "Informe o nome."}}` (uma mensagem por campo; o valor rejeitado não é devolvido) |
   | Restrição em `@RequestParam`/`@PathVariable` (validação de método do Spring) | 400 | `{"erro": "Dados inválidos.", "campos": {"codigo": "Código muito longo."}}` (chave = nome do parâmetro; exemplo genérico de `@Size(max = 5)`, não é regra de cliente) |
   | `BindException` lançada por validação programática (handler `parametrosInvalidos`, coberto por teste MockMvc próprio) | 400 | Mesmo formato de `campos`; o valor rejeitado não é devolvido |
   | JSON malformado ou de tipo errado | 400 | `{"erro": "Corpo da requisição inválido."}` |
   | Outro erro técnico do banco | 500 | `{"erro": "Não foi possível concluir a operação. Tente novamente."}` (detalhes só no log) |

   Controller de exemplo (sem `try/catch`; basta o Service lançar a exceção):
   ```java
   @RestController
   @RequestMapping("/clientes")
   class ClienteApiController {
       @PostMapping("/rapido")
       ClienteResumo cadastrar(@Valid @RequestBody ClienteRapidoForm form) {
           return servico.cadastrarRapido(form);   // RegraNegocioException vira 422 {"erro": "..."}
       }
   }
   ```
   No navegador, envie o cabeçalho CSRF (`<meta name="_csrf_header">` / `<meta name="_csrf">`) e mostre `corpo.erro` com
   `SnackFlow.toast`; para `campos`, marque cada campo do formulário.
   **Limites:**
   - Não há captura genérica de `Exception`. Acesso negado (403), rota inexistente (404), método não permitido (405),
     tipo de conteúdo não suportado (415) e erros inesperados seguem o padrão do Spring; **a chave `erro` não é
     garantida nessas respostas**, só nas produzidas pelo tratador JSON.
   - **Duplicidade tem duas camadas.** O erro bruto do índice único (SQLSTATE 23000, `DuplicateKeyException`, ex.: dois
     `INSERT` com o mesmo CPF direto pelo `JdbcTemplate`) **não** é regra sinalizada por trigger: o tratador o devolve como
     **500 genérico**, sem SQL, índice, CPF nem SQLSTATE. Já a **API de clientes** responde **422**: o Service verifica a
     duplicidade antes de gravar e, se uma corrida estourar o índice, captura `DuplicateKeyException` e lança
     `RegraNegocioException` com mensagem segura (seção 14). O 500 do teste de integração prova o tratamento seguro do erro
     bruto; não é o contrato final.
   - A mensagem de uma `RegraNegocioException` **vai direto ao cliente**: os Services devem escrever mensagens seguras,
     sem SQL, nomes de índice ou dados pessoais (CPF, telefone). Mensagens de trigger vêm do banco como estão (ex.:
     `CPF invalido`, sem acento, que é texto do trigger e não de interface); valide na aplicação primeiro para mostrar
     `CPF inválido.`.
   - **Log:** o tratador registra só contexto seguro (método e caminho da requisição, classe da exceção e da causa-raiz e
     SQLSTATE). Não registra a mensagem do driver nem o stack trace, que podem conter SQL e o CPF digitado. Ao logar em
     Services e Controllers, siga a mesma regra.
   - Estado da verificação dos testes `TratadorJsonDeExcecoesTest` e `TratadorJsonBancoIntegracaoTest`: veja
     `docs/PROGRESSO-FUNDACAO.md`.
6. **Redirecionamento de anônimo**: esta versão do Spring Security redireciona para `/login` **relativo**. Em testes use `redirectedUrl("/login")`, não um padrão com `**`.
7. **AssertJ e flash**: `RedirectAttributes.getFlashAttributes()` devolve `Map<String, ?>`; use `assertThat(flash.getFlashAttributes().get("erro")).isEqualTo("...")`, não `containsEntry`.
8. **Dados de exemplo**: o banco de teste nasce com os dados do script (5 funcionários, clientes, produtos, pedidos). Não dependa de ids fixos; crie dados próprios com nomes únicos ou limpe.
9. **Formulários (`th:field`)**: o formulário de exemplo dos testes é uma classe com getters e setters, e é o **único padrão validado**. **Não foi comprovado** que `record` funciona como `th:object` com `th:field`/componentes de campo (hipótese não testada: o acesso por propriedade JavaBean, `getNome()`, pode não enxergar o `nome()` do record); não há teste de renderização que prove nem refute. Use classe com getters e setters nos `Form`. Records continuam adequados para leitura (`JdbcTemplate`) e como corpo `@RequestBody` JSON. Se alguém quiser usar record em formulário, primeiro escreva um teste de renderização como o de `ComponentesIntegracaoTest`.
10. **Fragmentos não mesclam atributos**: atributos escritos na tag que chama `th:replace` são descartados. Precisa de `type="button"`, `onclick` ou `data-*` num botão? Use as classes `sf-btn ...` direto (seção 6).
11. **Rota nova**: sem linha em `Tela`, a rota cai em `anyRequest().authenticated()` (qualquer perfil logado acessa) e fica fora do menu. Sempre registre.
12. **Cache de templates**: com `THYMELEAF_CACHE=false` as telas atualizam sem reiniciar; em produção o padrão é cache ligado.
13. **`Tela` e `MenuService`** são do pacote `config`; módulos não os importam (o menu é montado pelo `LayoutAdvice`). `MenuService` só inclui telas com `Tela.apareceNoMenu()` (as que têm seção); rotas com `secao = null`, como `/clientes`, ficam fora do menu e continuam protegidas por perfil.

## 13. Como pedir seu módulo ao Claude

Anexe ao pedido: `docs/GUIA-DO-MODULO.md`, o *Documento de Planejamento*, o Design System, o `database/lanchonete.sql` e o protótipo HTML. Modelo de pedido:

```
Implemente o módulo <NOME> (pacote br.com.snackflow.<modulo>, rota <ROTA>), com as regras da seção 7.x do
planejamento e o guia do módulo. Use a fundação existente (config/Tela, layout, componentes, ErrosDoBanco).

Não altere arquivos compartilhados (seção 9 do guia). Se achar que precisa, pare e me diga o motivo.
Não implemente outros módulos nem invente regras fora do planejamento/SQL.

Entregue em etapas pequenas: (1) Repository e Service com testes unitários; (2) Controller e Form;
(3) templates usando fragments/layout e componentes; (4) testes de integração das regras críticas do banco.
Ao final de cada etapa, informe quais testes eu devo rodar (./mvnw verify) e o que NÃO foi validado.
Mensagens de erro em português, regra de negócio no Service, sem Lombok, injeção por construtor.
Sugira as mensagens de commit (Conventional Commits, uma finalidade por commit).
```

Pedidos pequenos funcionam melhor que "faça o módulo inteiro". Sempre cole o log do `./mvnw verify` quando algo falhar.

## 14. Contrato PDV ↔ clientes (decidido pelo responsável do projeto)

O planejamento (seção 7.3) diz que o PDV usa endpoints em `/clientes`, mas não fixa o formato. O contrato abaixo foi
**escolhido pelo responsável pelo projeto em 26/11/2026** e vale como decisão, sem etapa de ratificação por terceiros. Os
endpoints **ainda não existem**: o módulo `cliente` não foi implementado. Hoje só o tratador JSON (seção 12, item 5) e a
rota `/clientes` em `Tela` (CAIXA e ADMIN) estão prontos. Os controllers de `src/test` são infraestrutura de teste do
tratador e não definem o comportamento do módulo.

Acesso: perfis CAIXA e ADMIN (conforme `config/Tela`); CSRF continua exigido no POST (cabeçalho `_csrf_header`).

### Busca: `GET /clientes/busca?q=texto`

- Busca parcial por nome ou CPF, sem diferenciar maiúsculas de minúsculas.
- `q` é aparado. Se contiver só dígitos e pontuação de CPF (`.`, `-`, espaço), a busca é por CPF: remove-se a pontuação
  e comparam-se os dígitos. Caso contrário, é busca por nome.
- `q` ausente, vazio ou com menos de 2 caracteres úteis (para CPF, 2 dígitos após remover a pontuação): **`200 []`**.
  A pessoa ainda está digitando; não é erro e não há resposta 400 para busca curta.
- Só clientes **ativos** (`ativo = 1`), no máximo **10** resultados, ordenados por nome e, em caso de empate, por ID.
- Cada item tem `id`, `nome`, `cpf` e `telefone`. `cpf` é uma string só com dígitos ou `null` quando ausente; `telefone`
  segue o formato canônico abaixo ou é `null`. Formatar o CPF para exibição é trabalho da interface.

```json
[{"id": 12, "nome": "Mariana Costa", "cpf": "39053344705", "telefone": "(48) 98765-4321"}]
```

Os valores acima são só ilustrativos. O contrato e os testes **não dependem de IDs nem de dados fixos** do banco de exemplo.

### Cadastro rápido: `POST /clientes/rapido`

Entrada JSON: `{"nome": "Mariana Costa", "telefone": "(48) 98765-4321", "cpf": "39053344705"}`.

| Resultado | Status | Corpo |
|---|---|---|
| Criado | `201 Created` | `id`, `nome`, `cpf` e `telefone`, no mesmo formato da busca |
| Campo ausente, vazio ou malformado | `400 Bad Request` | `{"erro":"Dados inválidos.","campos":{"nome":"Informe o nome."}}` |
| Regra de negócio, incluindo CPF inválido ou duplicado | `422 Unprocessable Entity` | `{"erro":"Mensagem segura em português."}` |

Regras:

- **CPF** é opcional. Se informado, aceita pontuação, é validado (mesma regra de `fn_cpf_valido`) e **só os dígitos** são
  gravados. CPF inválido: `422` com `CPF inválido.` (a mensagem do trigger, `CPF invalido`, não vai ao usuário).
- **Duplicidade de CPF:** o Service verifica antes de gravar e responde `422` com mensagem segura, sem repetir o CPF. Se
  uma corrida de índice único (`uk_clientes_cpf`) escapar da verificação, o Service captura `DuplicateKeyException` e a
  converte em `RegraNegocioException`, que vira o mesmo `422`. O cliente existente **não** é selecionado automaticamente:
  o PDV mostra o erro e permite uma busca.
- **Nome:** aparar as extremidades e reduzir espaços consecutivos a um; preservar acentos e capitalização.
- **Telefone:** aceita entrada com pontuação; após remover o que não for dígito, exige 10 ou 11 dígitos brasileiros
  (DDD de dois dígitos iniciado de 1 a 9 e, com 11 dígitos, o número após o DDD começa com `9`). Armazena e responde no formato
  canônico `(DD) XXXX-XXXX` (10 dígitos) ou `(DD) 9XXXX-XXXX` (11 dígitos), e a interface exibe esse mesmo valor. Telefone
  inválido: `400` com a mensagem no campo `telefone`.
- O planejamento (7.3) lista nome, telefone e CPF opcional no cadastro rápido, então **telefone é obrigatório** nesse
  endpoint (ausente ou vazio: `400`). O banco aceita `NULL` (por isso a busca pode devolver `telefone: null`).

### Conferência com o planejamento e o banco

- Conforme: CPF opcional, único e validado (`uk_clientes_cpf`, `fn_cpf_valido`, triggers `trg_clientes_bi` e
  `trg_clientes_bu`), armazenado como 11 dígitos (`ck_clientes_cpf`); perfis CAIXA e ADMIN; não existe exclusão física
  (inativar).
- `telefone` é `VARCHAR(20) NULL` sem restrição de formato; o formato canônico (15 caracteres no máximo) cabe na coluna e
  coincide com os dados de exemplo. Nenhuma alteração de SQL ou migração é necessária.
- `clientes.ativo` existe; o planejamento não diz se a busca do PDV oculta inativos, e **a decisão tomada é mostrar só
  ativos**.
- O trigger devolve `CPF invalido` (sem acento) e a duplicidade chega como erro 23000; o Service trata os dois antes de
  responder, para devolver `422` em português (seção 12, item 5).

### Pendências de implementação (não de aprovação)

Os endpoints, o `ClienteService` (com testes de sucesso e de falha, de duplicidade verificada antes de gravar e da corrida de
índice único) e a integração do PDV ainda precisam ser feitos seguindo este contrato.
