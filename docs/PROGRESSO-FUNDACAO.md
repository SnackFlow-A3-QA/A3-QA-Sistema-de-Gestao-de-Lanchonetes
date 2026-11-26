# Progresso da fundação compartilhada

Atualizado em 26/11/2026. Este arquivo separa três coisas, sempre com a origem: o que foi **comprovado por execução** (log
do Maven ou da aplicação colado pelo responsável), o que foi **informado pelo responsável** (sem log ou captura anexado) e o
que **não foi validado**. Antes de iniciar um módulo, leia também o `docs/GUIA-DO-MODULO.md`.

O projeto tem um único responsável. As referências antigas a "membro 1", "membro 2" etc. eram do planejamento e não são
revisores nem bloqueios de aprovação: as decisões abaixo já foram tomadas por ele.

## 1. Resumo

- **`./mvnw verify` verde (log colado, execução de 26/11/2026, fim às 19:44:45):** `Tests run: 115, Failures: 0, Errors: 0,
  Skipped: 0`, `jacoco:check` atendido, `BUILD SUCCESS`, 1 min 12 s. Os 115 são os 112 anteriores mais os 3 testes desta
  rodada.
- **Aplicação e Compose (log colado, 19:46):** `docker compose up -d` iniciou `snackflow-mysql` e `./mvnw spring-boot:run`
  subiu a aplicação na porta 8080 conectada ao MySQL 8.4.13.
- **Informado pelo responsável (sem anexo):** validou as alterações desta rodada ("estão okay"); cobertura de 96% das
  instruções e 85% dos ramos (capturas anteriores); Compose funciona; abriu a aplicação no navegador e fez login.
- **Não validado:** o percentual de cobertura da execução de 115 testes (o log não o traz), o healthcheck do Compose, a
  verificação visual além do login, o PR, a CI e o Sonar.
- Não existe módulo funcional ainda: só infraestrutura compartilhada (segurança, layout, componentes, erros, ambiente e
  testes).

## 2. Comprovado por execução

### 2.1. Execução mais recente (Windows 11, JDK 27.0.1.1 compilando com `release 21`, Spring Boot 4.1.2, MySQL 8.4 via Docker)

Origem: log do PowerShell colado pelo responsável. O **trecho recebido começa no meio da execução** (no teste
`FuncionarioUserDetailsServiceTest`) e termina na aplicação em execução; o comando que iniciou o build verde e as demais
classes de teste **não aparecem**. O fim do log mostra as fases `jar`, `spring-boot:repackage`, `jacoco:report` e
`jacoco:check`, ou seja, a fase `verify`.

| Verificação | Resultado observado no log |
|---|---|
| Testes | `Tests run: 115, Failures: 0, Errors: 0, Skipped: 0` |
| Classes de teste visíveis no trecho | `FuncionarioUserDetailsServiceTest` (3), `PerfilTest` (5), `UsuarioLogadoTest` (4), `SnackFlowApplicationTests` (1), todas sem falha |
| Cobertura | `jacoco:check`: `All coverage checks have been met` (mínimo de 80% de linhas do `pom.xml`); 18 classes analisadas. **O percentual não aparece no log** |
| Build | `BUILD SUCCESS`, 01:12 min, fim às 19:44:45 (-03:00) |
| `docker compose up -d` | `Container snackflow-mysql Started` (0,3 s). **O healthcheck não aparece** |
| `mvn spring-boot:run` | Falhou no PowerShell: `mvn` não é reconhecido (Maven fora do `PATH`). Não é defeito do projeto; usa-se o wrapper |
| `./mvnw spring-boot:run` (19:46:26) | Subiu em 6,5 s na porta 8080 (Tomcat 11.0.27, Hibernate 7.4.13), conectou ao MySQL 8.4.13 em `localhost:3306/lanchonete`, 1 repositório JPA, `funcionarioUserDetailsService` ligado ao `AuthenticationManager`. O `DispatcherServlet` foi inicializado às 19:46:38, ou seja, houve ao menos uma requisição HTTP; **o que foi aberto no navegador não está no log** |

Inferência (não leitura direta): a contagem subiu de 112 para 115, igual ao número de testes acrescentados nesta rodada
(1 de `BindException` e 2 de menu), e não houve falha nem erro, então os testes novos foram compilados e passaram. Os
resultados de `MenuServiceTest`, `TratadorJsonDeExcecoesTest` e `TratadorJsonBancoIntegracaoTest` **não estão** no trecho
colado; vale o total agregado. A execução direcionada dessas classes, pedida antes do `verify`, também **não aparece** no log.

### 2.2. Execução anterior, 09:36 (mesmo ambiente; 112 testes)

Origem: logs do Maven colados pelo responsável em rodada anterior. O comando exato do build verde não
aparece no trecho recebido; o log mostra as fases `jar`, `spring-boot:repackage`, `jacoco:report` e `jacoco:check`, ou seja,
a fase `verify`.

| Verificação | Resultado observado no log |
|---|---|
| Testes (execução de 09:36:55) | `Tests run: 112, Failures: 0, Errors: 0, Skipped: 0` |
| Build | `BUILD SUCCESS`, 1 min no total |
| Cobertura | `jacoco:check`: `All coverage checks have been met` (mínimo de 80% de linhas do `pom.xml`); 18 classes analisadas |
| Compilação | 19 arquivos de `src/main` e 20 de `src/test`, sem erro |
| Aplicação (`./mvnw spring-boot:run`, 09:37) | Subiu em 3,6 s na porta 8080, conectou ao MySQL 8.4.13 em `localhost:3306/lanchonete`, Hibernate e Security inicializados, 1 repositório JPA encontrado |

Histórico daquela rodada, na ordem em que aconteceu:

1. Relatório Surefire antes das correções (109 testes, 2 falhas): as duas de `TratadorJsonBancoIntegracaoTest`, com
   `Response status expected:<422> but was:<404>` e `Status expected:<200> but was:<404>`.
2. Primeira execução após as correções, **sem o Docker aberto**: 112 testes, 40 erros, todos de `ApplicationContext failure
   threshold (1) exceeded` (o contexto Spring com Testcontainers não subiu). Causa ambiental, não de código.
3. Segunda execução, com Docker: 112 testes, 1 falha. A asserção do log em `TratadorJsonBancoIntegracaoTest` proibia a
   palavra `Duplicate`, que aparece no nome da classe `DuplicateKeyException` registrada de propósito. O log estava correto
   (só método, caminho, classe da exceção, causa-raiz e SQLSTATE); a asserção foi corrigida para proibir `Duplicate entry`.
4. Terceira execução: 112 testes, 0 falhas, `BUILD SUCCESS`.

**Causa do 404 (confirmada por essa execução):** `InsercaoDeExemplo` é um `@RestController` **aninhado na classe de teste**.
O Spring Boot não o varre como componente, então ele não era handler no contexto e a rota `/teste/api-banco/cliente` não
existia. `SegurancaELayoutIntegracaoTest` tem o mesmo setup e passa porque seu controller de exemplo (`/teste/layout`) é uma
classe de nível superior em `src/test`. Com o `@Import` e a checagem do mapeamento no `@BeforeEach`, o 404 desapareceu na
terceira execução.

### 2.3. Inspeção estática de rodada anterior (ambiente em nuvem)

Sem Maven, Docker nem rede, só leitura. Inspeção do `target/snackflow-0.0.1-SNAPSHOT.jar`: `HandlerMethodValidationException`
em `spring-web-7.0.10`; `ParameterErrors` e `ParameterValidationResult` em `spring-context-7.0.10`; `logback-classic-1.5.38`.
A compilação e os testes dessa rodada só passaram a ter evidência com a execução da seção 2.2.

## 3. Informado pelo responsável (sem log ou captura anexado)

Fonte: mensagens do responsável. As capturas do relatório JaCoCo **não foram anexadas** a esta documentação; os valores são
os que ele relatou.

| Item | Informado |
|---|---|
| Alterações desta rodada | "Validei tudo, as alterações estão okay" (mensagem de 26/11/2026). Vale como conferência do responsável; a evidência de execução é o `verify` da seção 2.1 |
| Cobertura JaCoCo, total | **96% das instruções** e **85% dos ramos** |
| Cobertura por pacote | `common`: 90% das instruções, 77% dos ramos; `config`: 100%; `funcionario`: 100% |
| Não coberto no total | 5 linhas e 1 método. O denominador e o percentual de **linhas** não aparecem na captura e **não são inventados aqui**; qual é o método não coberto também não foi informado |
| Docker Compose | "Funciona" (o log da seção 2.1 mostra o contêiner iniciado) |
| Navegador | Abriu a aplicação no navegador e **conseguiu fazer login**. Nada além disso foi relatado |
| GitHub | Proteção da `main` e `SONAR_TOKEN` configurados |
| Pull Request | **Ainda não aberto** (última informação recebida) |

**Atenção à cobertura:** não foi informado se as capturas de 96%/85% vieram antes ou depois dos 3 testes desta rodada, e o
log da seção 2.1 não traz o percentual. Trate esses números como **anteriores ou não confirmados** para a execução de 115
testes; substitua-os pelo `target/site/jacoco/index.html` desta execução (ação 1 da seção 6).

O que **não** se pode concluir dessas informações: que modal, spinner, tela estreita (~375 px), toasts, menu por perfil,
logout ou qualquer outro fluxo visual foram inspecionados; que o healthcheck do Compose chegou a `healthy`; nem o conteúdo do
banco.

## 4. Alterações desta rodada

Ao iniciar esta rodada, a árvore já continha as correções da rodada anterior (as que a execução da seção 2.2 validou):
`@Import` do controller aninhado e checagem do `RequestMappingHandlerMapping` em `TratadorJsonBancoIntegracaoTest`, o teste
de duplicidade renomeado para `deveResponder500GenericoNoErroBrutoDeIndiceUnicoDeCpfNaPersistenciaDireta`, o log seguro do
`TratadorJsonDeExcecoes` e a seção 14 do guia como decisão do responsável. Elas foram conferidas por **leitura**, não
alteradas. Não havia `.git` na cópia usada para editar, então `git status` não pôde ser consultado; o trabalho existente foi
preservado editando só os arquivos abaixo.

| Arquivo | Alteração | Execução |
|---|---|---|
| `src/main/.../config/MenuService.java` | `montar` passa a filtrar por `tela.apareceNoMenu()` antes de seção e perfil. Comportamento igual ao anterior: `/clientes` (sem seção) continua fora do menu | Incluída no `verify` verde (seção 2.1) |
| `src/test/.../config/MenuServiceTest.java` | Dois testes novos: toda tela com seção permitida ao perfil aparece no menu (para cada `Perfil`) e `/clientes` não aparece para CAIXA nem ADMIN, embora permitida | Idem; resultado individual não aparece no trecho do log |
| `src/test/.../common/TratadorJsonDeExcecoesTest.java` | Teste novo `deveResponder400ComCamposEmBindExceptionSemRepetirOValorRejeitado`: um endpoint de exemplo lança uma `BindException` pura (`MapBindingResult`, valor `valor-secreto-123`, duas mensagens para o mesmo campo); confere 400, `erro`, `campos.telefone` com a primeira mensagem e que a resposta não repete o valor nem a segunda mensagem. Exercita `parametrosInvalidos(BindException)`, antes sem execução. O handler foi **mantido** | Idem |
| `CONTRIBUTING.md` | Fluxo de responsável único: issue, branch de trabalho, `./mvnw verify`, PR do responsável, CI e Sonar verdes, merge protegido pela regra da branch; sem aprovação de colega. Seção 5 virou organização histórica dos módulos (1 a 4), sem nomes | n/a |
| `docs/GUIA-DO-MODULO.md` | Linha de `BindException` na tabela da seção 12, nota sobre `apareceNoMenu()` no item 13, DDD alinhado à decisão ("iniciado de 1 a 9") e uso do `./mvnw` em vez de `mvn` (seção 8 e testes), com a nota sobre o `PATH` | n/a |
| `docs/PROGRESSO-FUNDACAO.md` | Este arquivo | n/a |

A contagem passou de 112 para 115 (1 teste de `BindException` e 2 de menu). Não foram alterados `pom.xml`,
`database/lanchonete.sql`, segurança, CI, Compose, layout, CSS nem JS, e não foi criado nenhum endpoint `/clientes/*`,
tela, repository ou service de negócio. O limite de 80% do JaCoCo não foi mexido.

### 4.1. Decisões registradas (contrato PDV ↔ clientes e tratador)

Decididas pelo responsável (guia, seção 14): busca `GET /clientes/busca?q=` (`200 []` abaixo de 2 caracteres úteis, só
ativos, máximo 10, ordem por nome e ID, CPF só com dígitos ou `null`); cadastro `POST /clientes/rapido` (201; 400 de
validação; 422 de regra, incluindo CPF inválido ou duplicado, sem selecionar o cliente existente); duplicidade verificada
antes de gravar com conversão de `DuplicateKeyException` em `RegraNegocioException`; nome normalizado; telefone no formato
canônico `(DD) XXXX-XXXX` ou `(DD) 9XXXX-XXXX`; perfis CAIXA e ADMIN com CSRF. **Nada disso está implementado**: são regras
para o módulo `cliente` e a integração do PDV.

Pontos que o responsável confirmou como decisão nesta rodada: telefone **obrigatório** no cadastro rápido (o banco aceita
`NULL`; o planejamento 7.3 lista telefone junto com o nome) e DDD de dois dígitos iniciado de 1 a 9, com o número após o
DDD começando com `9` quando houver 11 dígitos. Conferência com o banco: `telefone` é `VARCHAR(20) NULL` sem restrição de
formato e o formato canônico (15 caracteres) cabe nele; nenhuma alteração de SQL ou migração foi necessária. A conferência
com "dados existentes" foi só leitura do SQL e do protótipo; o banco real não foi consultado.

Tratador (`TratadorJsonDeExcecoes`, revisado por leitura): só `@RestController`; 400 com `campos` sem repetir o valor;
JSON malformado 400 genérico; regra e SQLSTATE 45000 em 422; falha técnica 500 genérico; sem captura de `Exception`.
`@Controller` continua com `TratadorGlobalDeExcecoes` (redirect, `Referer`, flash).

## 5. Não validado

- **Percentual de cobertura da execução de 115 testes**: o log não o traz; os 96%/85% da seção 3 são informados e de data
  incerta.
- **Resultado individual dos testes novos**: só o total agregado (115, 0 falhas, 0 erros) está no log; o trecho colado não
  inclui `MenuServiceTest`, `TratadorJsonDeExcecoesTest` nem `TratadorJsonBancoIntegracaoTest`.
- **Execução direcionada prévia** (tratador, `BindException`, menu e integração MySQL): não aparece em nenhum log recebido.
- **Healthcheck do Compose e conteúdo do banco**: o log mostra o contêiner iniciado, mas não `docker compose ps` em
  `healthy` nem a consulta a `funcionarios`.
- **Navegador**: só o login foi relatado. Matriz de 403 por perfil, menu, toasts, modal, spinner, tela estreita (~375 px) e
  logout seguem **sem verificação visual** (segurança e menu têm testes automatizados que passam no `verify`). Tailwind,
  Lucide e Inter vêm de CDN.
- **CI e SonarCloud**: não executados nem alterados. Leitura estática do workflow: Java 21, `mvn -B verify` com Sonar e
  `sonar.qualitygate.wait=true`. A proteção da `main` e o `SONAR_TOKEN` foram informados como configurados; **não foram
  verificados**. O build local usou JDK 27 compilando com `release 21`; a CI usa Java 21.
- **Pull Request**: não aberto, segundo a última informação; portanto sem resultado de CI nem Sonar.
- **Contrato PDV ↔ clientes**: decisão registrada, **sem implementação nem teste de módulo**; os controllers de exemplo em
  `src/test` provam o formato do tratador, não o futuro `ClienteService`.

## 6. Próximas ações do responsável

1. **Registrar a cobertura desta execução**: abrir `target/site/jacoco/index.html` (gerado pelo `verify` de 19:44) e
   substituir na seção 3 os números de 96%/85% pelos desta execução, mantendo os anteriores como histórico.
2. **Conferir o Compose** (nada apaga dados): `docker compose ps` até `healthy` e `docker exec snackflow-mysql mysql -uroot
   -proot lanchonete -e "SELECT login, perfil, ativo FROM funcionarios;"` (5 linhas). Só `docker compose down -v` apaga o
   banco.
3. **Verificação no navegador além do login**, se desejado (`./mvnw spring-boot:run`, `http://localhost:8080`, senha
   `snackflow123`): perfis, menu, 403 em URL de outro perfil, largura de ~375 px, logout. As telas de exemplo (`/teste/*`)
   existem só em `src/test`.
4. **Abrir o PR da fundação** e conferir CI e Sonar.
5. **Opcional:** trocar `mvn` por `./mvnw` no `README.md` (linhas 64 e 67) e em `.github/PULL_REQUEST_TEMPLATE.md`, que
   ainda citam `mvn` e falham em máquinas sem Maven no `PATH`. Não foram alterados nesta rodada.

## 7. O que a fundação entrega

| Área | Itens |
|---|---|
| `common` | `RegraNegocioException`, `ErrosDoBanco`, `TratadorGlobalDeExcecoes` (telas: volta à anterior com toast), `TratadorJsonDeExcecoes` (JSON) |
| `funcionario` | `Perfil`, `Funcionario`, `FuncionarioRepository`, `UsuarioLogado`, `FuncionarioUserDetailsService` |
| `config` | `Tela` (fonte única de rotas, perfis e menu), `MenuService`, `LayoutAdvice`, `SegurancaConfig`, `RedirecionamentoPorPerfilHandler`, `HomeController`, `LoginController` |
| Templates | `fragments/layout`, `base`, `componentes`, `erro`, `login`, `error/403, 404, 4xx, 5xx` |
| Estáticos | `snackflow.css`, `snackflow.js`, `tailwind-config.js` |
| Ambiente | `application.properties`, `docker-compose.yml` com healthcheck, `.editorconfig` |
| Documentação | `GUIA-DO-MODULO.md`, `README.md`, `CONTRIBUTING.md` |

Pastas de módulo preparadas (sem classes vazias): `cliente`, `cozinha`, `estoque`, `pedido`, `produto`, `relatorio`.

## 8. Decisões e limitações conhecidas

1. `Tela` centraliza rota, perfis e menu; `SegurancaConfig` e `MenuService` leem dela. `MenuService` usa
   `apareceNoMenu()`. Rota nova = uma linha.
2. Telas: o tratador global **redireciona**. Endpoints `@RestController`: o tratador JSON responde com status e JSON. Um
   `@Controller` com `@ResponseBody` não é coberto pelo tratador JSON.
3. A chave `erro` é garantida pelas respostas do tratador JSON, não por 403/404/405/415 padrão do Spring.
4. O erro bruto de índice único na persistência direta vira 500 genérico; na API de clientes, a duplicidade é 422 tratada
   pelo Service (guia, seções 12 e 14).
5. O CSS dos componentes é CSS puro (`sf-*`), independente do Tailwind CDN.
6. `Funcionario` só expõe `setAtivo`; o CRUD de funcionários exigirá ampliá-la sem quebrar a autenticação.
7. Records como `th:object`: não comprovado; usar classe com getters e setters.
8. O edital da A3 avalia a distribuição de commits entre os integrantes e exige a conta pessoal de cada um. O fluxo de
   responsável único do `CONTRIBUTING.md` é uma decisão do responsável; confira com o professor se o grupo de 5 do edital
   se aplica a este trabalho.
9. Use `./mvnw`, não `mvn`: o Maven não está no `PATH` do PowerShell do responsável (seção 2.1). O `ci.yml` chama `mvn` direto; de onde vem o Maven na CI não foi verificado.

## 9. Próximos passos

1. Executar as ações da seção 6 e atualizar este arquivo com o observado.
2. Com o `verify` verde (seção 2.1), os módulos podem começar do ponto de vista do build: **produtos** e **estoque** (não
   dependem de clientes), **cozinha**, e **pedido** na parte que não envolve a busca de clientes. Isso **não** significa que
   o PR, a CI ou o Sonar já passaram: o PR ainda não foi aberto. A verificação visual é recomendada antes da primeira tela
   de módulo, mas não é pré-requisito de produtos e estoque.
3. O módulo `cliente` e a busca e o cadastro de clientes no PDV seguem o contrato da seção 14 do guia.
4. Depois: funcionários e relatórios, usando o modelo de pedido da seção 13 do guia.
