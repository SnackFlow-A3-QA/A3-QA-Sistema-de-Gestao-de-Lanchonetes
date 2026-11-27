# Progresso da fundação compartilhada

Atualizado em 26/11/2026. Este arquivo separa o que foi **comprovado por execução**, o que **não foi validado** e as
**ações humanas** pendentes. Antes de iniciar um módulo, leia também o `docs/GUIA-DO-MODULO.md`.

## 1. Resultado

**A fundação tem o código e os testes necessários, mas esta execução NÃO conseguiu reconfirmar o build.** Não existe
nenhum módulo funcional ainda: só infraestrutura compartilhada (segurança, layout, componentes, erros, ambiente e testes).
A fundação só pode ser dada como pronta depois de um `./mvnw verify` verde na máquina de alguém com Docker e internet
(seção 5, ação 1).

## 2. Comprovado

### 2.1. Registro anterior (execução na máquina da equipe, informada pelo responsável; não repetida aqui)

Origem: logs do `./mvnw verify` (JDK 27 compilando com `release 21`, Docker Desktop aberto) e o relatório do JaCoCo.

| Item | Evidência |
|---|---|
| Compilação do código principal e dos testes | Compilou após a correção de 3 asserções de tipagem em `TratadorGlobalDeExcecoesTest` |
| Suíte de testes | 92 executados: no primeiro log, 91 passaram e 1 falhou (`deveRedirecionarAnonimoParaLogin`, expectativa incompatível com o redirecionamento relativo `/login`). A asserção foi corrigida para `redirectedUrl("/login")` |
| Contexto Spring completo + MySQL via Testcontainers com `lanchonete.sql` | Testes de integração executaram sem erro |
| Segurança e renderização do layout/componentes | Cobertos por `SegurancaELayoutIntegracaoTest` e `ComponentesIntegracaoTest` |
| Cobertura (`common`, `config`, `funcionario`: 17 classes) | 99% das instruções, 100% das linhas, 91% das ramificações; `Tela` com 50% das ramificações |

O log completo do `verify` **após** a correção do teste de redirecionamento nunca foi anexado.

### 2.2. Observado nesta execução (26/11/2026, ambiente em nuvem sem acesso a Maven Central nem Docker Hub)

| Verificação | Comando | Resultado observado |
|---|---|---|
| Extração do projeto | leitura do `.rar` | 88 arquivos extraídos; o estado do código bate com o descrito neste arquivo |
| Java e Maven disponíveis | `java -version`, `mvn -v` | OpenJDK 21.0.13 e Maven 3.9.11 instalados |
| `./mvnw verify` | `./mvnw -B verify` | **Falhou antes de compilar**: `wget: Failed to fetch https://repo.maven.apache.org/maven2/.../apache-maven-3.10.0-bin.zip`. O proxy do ambiente respondeu 403 para `repo.maven.apache.org` |
| Build com o Maven local | `mvn -B -q -DskipTests compile` | **Falhou**: `Non-resolvable parent POM ... spring-boot-starter-parent:pom:4.1.2 ... Unknown host repo.maven.apache.org`. Não há nenhum jar do Spring em cache no ambiente |
| Imagem do banco | `docker pull mysql:8.4` | **Falhou**: `registry-1.docker.io ... Forbidden (agent proxy: no rule or network setting allows this host)` |
| Daemon do Docker | `dockerd` iniciado manualmente | Subiu (`docker info` respondeu); sem imagem, não subiu contêiner nenhum |
| Sintaxe dos arquivos novos | `javac -proc:none` sobre os 3 arquivos Java alterados, ignorando erros de dependência ausente | Nenhum erro de sintaxe. **Isto não é compilação**: os tipos do Spring não puderam ser resolvidos |
| YAML do Compose e da CI | `python3 yaml.safe_load`; `docker compose config -q` | Válidos (`docker compose config` saiu com 0). Só valida a estrutura, não o healthcheck em execução |

Conclusão: **nenhum teste foi executado nesta execução e nenhuma cobertura foi medida.** Os números da seção 2.1 são
os do registro anterior, não os de hoje.

## 3. O que foi alterado nesta execução (sem compilar nem testar)

| Arquivo | Alteração | Por quê |
|---|---|---|
| `common/TratadorJsonDeExcecoes.java` (novo) | `@RestControllerAdvice(annotations = RestController.class)` com `@Order(HIGHEST_PRECEDENCE)`. Trata `RegraNegocioException` (422), `DataAccessException` (422 se mensagem de trigger, senão 500 genérico), validação (400 com `campos`) e JSON ilegível (400). Sem captura genérica de `Exception` | Lacuna confirmada: o tratador atual redireciona e devolveria HTML a um `fetch`. Vale para PDV e clientes. O tratador de telas **não foi tocado** |
| `test/.../common/TratadorJsonDeExcecoesTest.java` (novo) | MockMvc autônomo (sem banco nem Docker): sucesso, 422, validação, JSON malformado, banco com e sem mensagem de trigger, valor rejeitado e detalhes técnicos fora da resposta, e **3 testes de redirecionamento de tela com `Referer`** (com Referer, sem Referer, host do Referer descartado) | Pedido: sucesso, erro de validação, erro de negócio; preservar o comportamento das telas; cobrir `Referer` em requisição real |
| `test/.../config/TelaTest.java` | Novo teste `telaComSecaoDeveAparecerNoMenuDoAdmin` | Ver abaixo |
| `docs/GUIA-DO-MODULO.md` | Seções 1, 5, 12 (itens 5 e 9) e 14 | Formato das respostas JSON; limitação dos records; contrato marcado como proposta com suposições |
| `README.md` | Uma frase da seção "Fundação compartilhada" | Passou a existir tratamento JSON |

Arquivos compartilhados tocados: apenas `common/` (novo arquivo) e `docs/`. Não foram alterados `pom.xml`, banco,
segurança, `Tela`, `application.properties`, CI nem Compose.

**Sobre `Tela` (50% das ramificações):** a única ramificação de `Tela` é `secao != null` em `apareceNoMenu()`. Esse método
**não é chamado por nenhum código de produção** (o `MenuService` compara `tela.secao()` diretamente); só o `TelaTest`
chama, e só com `CLIENTES` (falso). O ramo "verdadeiro" ficou sem cobertura. O teste novo cobre o ramo e confere que as
telas com seção são exatamente as que aparecem no menu do ADMIN. Proposta para a equipe, não aplicada: usar
`apareceNoMenu()` no `MenuService` ou remover o método.

**Records como `th:object`:** **não implementado nem comprovado.** Sem build não há como provar com teste de renderização,
então o padrão validado (classe com getters e setters) foi mantido e o guia (seção 12, item 9) agora diz isso
explicitamente. Receita para quem for validar: copiar `TelaDeFormularioTesteController` com um record e checar, no
`ComponentesIntegracaoTest`, valor mantido e `sf-invalido` em campo com erro.

### 3.1. Segunda rodada: contrato PDV ↔ clientes e tratador JSON

Estado de partida: a árvore recebida já continha as alterações não validadas da rodada anterior (seção 3); nada nela foi
compilado ou testado. **Esta rodada também não compilou nem executou nada.** Repetidos hoje, com o mesmo resultado:
`./mvnw -B verify` → `wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip`;
`mvn -B -q -DskipTests compile` → `[FATAL] Non-resolvable parent POM ... spring-boot-starter-parent:pom:4.1.2 ... repo.maven.apache.org: Name or service not known`;
`docker pull mysql:8.4` → daemon indisponível nesta sessão (e, na tentativa anterior, `registry-1.docker.io ... Forbidden`).
Por isso a versão efetiva do Spring **não pôde ser conferida em jars resolvidos**: só se sabe que o `pom.xml` declara
`spring-boot-starter-parent` 4.1.2 (linha Spring Framework 7). Os tipos escolhidos existem desde o Spring 6.1.

| Arquivo | Alteração |
|---|---|
| `common/TratadorJsonDeExcecoes.java` | Novo handler de `HandlerMethodValidationException` (restrições em `@RequestParam`/`@PathVariable`): 400, mesmo formato, chave = nome do parâmetro. Refatorado para um único montador do corpo de validação |
| `TratadorJsonDeExcecoesTest.java` | Cobre: 201 com cliente (e `cpf` nulo), `200 []` para busca ausente/curta, 400 de campo com mensagem e sem valor rejeitado, 400 de parâmetro, JSON malformado, 422 de regra e de trigger, 500 técnico sem detalhes, e as páginas `@Controller` continuando a redirecionar com flash `erro` |
| `TratadorJsonBancoIntegracaoTest.java` (novo) | Com Testcontainers e dados próprios: CPF inválido → 422 `CPF invalido` (mensagem real do trigger); CPF repetido → 500 genérico (índice único não é trigger). Remove o que cria |
| `docs/GUIA-DO-MODULO.md` | Itens 5 e 14 reescritos para coincidir com o código; contrato marcado como **decisões propostas, aguardando ratificação dos membros 1 e 2** |

Decisões codificadas (somente o tratador e os testes; os endpoints `/clientes/*` **não existem**): 422 para regra e trigger,
400 para validação e JSON ilegível, 500 genérico para erro técnico, sem captura de `Exception`. Os testes de "contrato"
usam controllers fictícios só de teste: provam o formato do tratador, **não** o comportamento do futuro módulo.

Achados que afetam quem for implementar clientes:
- CPF repetido chega como SQLSTATE 23000, não 45000: sem tratamento no Service, vira **500**, não 422.
- O trigger devolve `CPF invalido` sem acento; validar na aplicação antes para dar texto em português.
- A proposta antiga mostrava CPF formatado na resposta; o banco guarda 11 dígitos, então a resposta passou a ter só dígitos.
- O planejamento não diz se a busca oculta clientes inativos (`clientes.ativo`); a proposta é ocultar.

### 3.2. Falha do SonarCloud na CI (relato do pull request)

O quality gate reprovou por 1 issue **Blocker** de segurança: `javasecurity:S5146` (redirecionamento aberto) em
`HomeController.inicio()`, linha 17 (`"redirect:" + usuario.getPerfil().getPaginaInicial()`). O valor vem de um `enum`,
não do usuário, então na prática é falso positivo da análise de fluxo, mas o gate bloqueia do mesmo jeito.
Correção aplicada (**não compilada nem testada**): `switch` sobre `Perfil` devolvendo destinos literais; novo teste em
`HomeELoginControllerTest` confere que, para todo perfil, o destino coincide com `Perfil.getPaginaInicial()`.
Observação: `./mvnw verify` **não executa o Sonar**; só a CI roda `verify` junto com `sonar-maven-plugin:sonar`.
Para reproduzir localmente é preciso `SONAR_TOKEN` e o comando completo do `ci.yml`.

## 4. Não validado

- **`./mvnw verify` e cobertura**: não executados (seção 2.2). Os arquivos novos de Java e os testes novos **nunca foram
  compilados**. Pontos que mais merecem atenção se falharem: a compatibilidade de `MethodArgumentNotValidException` e
  `BindException` na versão do Spring desta stack, o `record` aninhado como corpo no teste e a serialização JSON no MockMvc autônomo.
- **`docker compose up -d` e o healthcheck**: não executados (sem imagem). Só foi validada a estrutura do arquivo.
  Passos de validação manual estão na seção 5, ação 2.
- **Navegador**: a aplicação não pôde subir (sem build e sem banco), então **nenhuma tela foi aberta**. Login e menu dos
  4 perfis, layout, toasts, modal, spinner e tela estreita continuam sem verificação visual. Roteiro na seção 5, ação 3.
  Tailwind, Lucide e Inter vêm de CDN; sem internet os ícones e utilidades não aparecem.
- **CI e SonarCloud**: não executados (dependem de GitHub, conta e segredo). Comparação **estática** do workflow com o uso local:
  - o workflow roda `mvn -B verify` com o Maven do runner; localmente o projeto fixa o Maven 3.10.0 pelo wrapper (`./mvnw`). A divergência de versão de Maven é um risco pequeno, não um erro;
  - `ubuntu-latest` tem Docker, então o Testcontainers deve funcionar, mas não foi provado;
  - o `verify` e a análise Sonar rodam no mesmo comando, e `sonar.qualitygate.wait=true` faz o job falhar se o quality gate reprovar;
  - o relatório JaCoCo (`target/site/jacoco/jacoco.xml`) bate com `sonar.coverage.jacoco.xmlReportPaths` do `pom.xml`;
  - `SONAR_TOKEN` precisa existir nos segredos do repositório (não verificável daqui); PRs vindos de forks não recebem segredos;
  - as versões das ações (`checkout@v6`, `setup-java@v5`, `cache@v5`) não foram conferidas.
- **Contrato PDV ↔ clientes**: continua **proposta** (guia, seção 14), agora com as suposições listadas.

## 5. Ações humanas pendentes

1. **Rodar `./mvnw verify`** com Docker aberto e internet, e anexar o log completo. Se falhar nos arquivos novos
   (`TratadorJsonDeExcecoes*`, `TelaTest`), corrigir antes do PR. Conferir a cobertura final e o `Tela` no JaCoCo.
2. **Validar o Compose** (nada apaga dados):
   `docker compose up -d`, `docker compose ps` até aparecer `healthy`, depois
   `docker exec snackflow-mysql mysql -uroot -proot lanchonete -e "SELECT login, perfil, ativo FROM funcionarios;"` e
   conferir 5 linhas. Só `docker compose down -v` apaga o banco: use apenas se quiser recriar.
3. **Verificação no navegador** (`./mvnw spring-boot:run`, `http://localhost:8080`), senha `snackflow123`:
   - `admin` e `ana.caixa` caem em `/pdv`; `marcos.cozinha` em `/cozinha`; `julia.estoque` em `/estoque`; `roberto.antigo` é recusado;
   - o menu mostra só as rotas do perfil (ADMIN: 6; CAIXA: PDV; COZINHA: Cozinha; ESTOQUE: Estoque); URL de outro perfil mostra a página 403;
   - com as ferramentas de desenvolvedor, largura de ~375 px: o menu recolhe, não há rolagem horizontal;
   - toasts, modal de confirmação e spinner: as telas de exemplo (`/teste/layout`, `/teste/formulario`) existem só em `src/test` e **não sobem com `spring-boot:run`**; valide na primeira tela de módulo ou, para um teste isolado, crie localmente (sem commitar) uma classe de aplicação de teste que as inclua;
   - sair pelo botão (POST) volta a `/login?saiu`.
4. **Abrir o PR da fundação**, com CI e Sonar, e **cadastrar `SONAR_TOKEN`** nos segredos do GitHub (se ainda não existir).
   No SonarCloud, desligar a análise automática se estiver ligada (conflita com a análise da CI).
5. **Proteger a branch `main`** no GitHub (exigir PR e CI verde).
6. **Confirmar com o professor** a equipe de 4 integrantes (membro 1).
7. **Ratificar o contrato PDV ↔ clientes** (membros 1 e 2): lista na seção 14 do guia (200 `[]` em busca curta, 201, 400 × 422, CPF só com dígitos, só ativos, limite de 10, CPF duplicado sem seleção automática). Nada disso foi aprovado ainda.
8. **Aprovar o `TratadorJsonDeExcecoes`** (membro 2, dono de `config`/arquivos compartilhados): é arquivo novo em `common/`.
9. Decidir se `apareceNoMenu()` passa a ser usado no `MenuService` ou é removido.

## 6. O que a fundação entrega

| Área | Itens |
|---|---|
| `common` | `RegraNegocioException`, `ErrosDoBanco`, `TratadorGlobalDeExcecoes` (telas: volta à anterior com toast), `TratadorJsonDeExcecoes` (JSON; **novo, não compilado**) |
| `funcionario` | `Perfil`, `Funcionario`, `FuncionarioRepository`, `UsuarioLogado`, `FuncionarioUserDetailsService` |
| `config` | `Tela` (fonte única de rotas, perfis e menu), `MenuService`, `LayoutAdvice`, `SegurancaConfig`, `RedirecionamentoPorPerfilHandler`, `HomeController`, `LoginController` |
| Templates | `fragments/layout`, `base`, `componentes`, `erro`, `login`, `error/403, 404, 4xx, 5xx` |
| Estáticos | `snackflow.css`, `snackflow.js`, `tailwind-config.js` |
| Ambiente | `application.properties`, `docker-compose.yml` com healthcheck, `.editorconfig` |
| Documentação | `GUIA-DO-MODULO.md`, `README.md`, `CONTRIBUTING.md` |

Pastas de módulo preparadas (sem classes vazias): `cliente`, `cozinha`, `estoque`, `pedido`, `produto`, `relatorio`.

## 7. Decisões e limitações conhecidas

1. `Tela` centraliza rota, perfis e menu; `SegurancaConfig` e `MenuService` leem dela. Rota nova = uma linha.
2. Telas: o tratador global **redireciona**. Endpoints `@RestController`: o tratador JSON responde com status e JSON. Um
   `@Controller` com `@ResponseBody` não é coberto pelo tratador JSON.
3. O CSS dos componentes é CSS puro (`sf-*`), independente do Tailwind CDN.
4. `Funcionario` só expõe `setAtivo`. O CRUD de funcionários (membro 2) exigirá ampliá-la sem quebrar a autenticação.
5. Tailwind, Lucide e Inter vêm de CDN; sem internet o visual degrada.
6. Records como `th:object`: não comprovado; usar classe.

## 8. Próximos passos

1. Executar as ações humanas 1 a 3 e atualizar este arquivo com os resultados reais.
2. Abrir o PR, ativar CI/Sonar e a proteção da `main`.
3. Fechar o contrato PDV ↔ clientes e aprovar o tratador JSON.
4. Iniciar os módulos na ordem do cronograma: **produtos** (fecha o issue do professor), estoque, PDV, clientes, cozinha;
   depois funcionários e relatórios. Use o modelo de pedido da seção 13 do guia.
