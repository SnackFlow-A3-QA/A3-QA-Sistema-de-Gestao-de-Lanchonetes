# Progresso da fundação compartilhada

Atualizado em 27/11/2026. Este arquivo registra o estado da base entregue aos módulos: o que foi **comprovado**, o que
**ainda não foi verificado** e o que fica **decidido**. Antes de iniciar um módulo, leia também o `docs/GUIA-DO-MODULO.md`.

## 1. Resultado

**A fundação está pronta e validada.** A `main` passa no CI (testes, JaCoCo com mínimo de 80% de linhas e SonarCloud com
quality gate aprovado), e os registros do JaCoCo foram feitos pelo responsável do projeto. Não existe ainda nenhum módulo
funcional: só infraestrutura compartilhada (segurança, layout, componentes, erros, ambiente e testes). Os módulos são
desenvolvidos em PRs independentes, em qualquer ordem (guia, seções 11 e 14).

## 2. Comprovado

| Item | Evidência |
|---|---|
| Build, testes e cobertura | CI verde na `main`: `mvn -B verify` com Testcontainers (MySQL 8.4 com `lanchonete.sql`) e JaCoCo com regra mínima de 80% de linhas. Números exatos: relatório do JaCoCo e execução do CI no GitHub |
| SonarCloud | Quality gate aprovado (`sonar.qualitygate.wait=true`). Os registros do JaCoCo foram feitos pelo responsável |
| Segurança e layout | `SegurancaELayoutIntegracaoTest` e `ComponentesIntegracaoTest` (contexto Spring completo com MySQL real) |
| Tratamento de erros | `TratadorGlobalDeExcecoesTest`, `TratadorJsonDeExcecoesTest` (MockMvc autônomo) e `TratadorJsonBancoIntegracaoTest` (trigger de CPF e índice único com MySQL real) |
| Proteção da `main` e `SONAR_TOKEN` | Configurados pelo responsável (conferir em *Settings → Branches* e *Secrets* se houver dúvida) |

Histórico que explica decisões do código:

- Na primeira validação local (92 testes), uma asserção falhou (`deveRedirecionarAnonimoParaLogin`) porque o Spring Security
  redireciona para `/login` relativo; a asserção passou a usar `redirectedUrl("/login")`.
- O SonarCloud reprovou o gate por 1 issue Blocker (`javasecurity:S5146`, redirecionamento aberto) em
  `HomeController.inicio()`. O valor vinha de um `enum`, mas o gate bloqueava mesmo assim. Corrigido com um `switch` sobre
  `Perfil` com destinos literais; `HomeELoginControllerTest` garante que coincidem com `Perfil.getPaginaInicial()`.
- `./mvnw verify` **não executa o Sonar**; só a CI roda `verify` junto com `sonar-maven-plugin:sonar`. Para reproduzir
  localmente é preciso `SONAR_TOKEN` e o comando completo do `ci.yml`.

## 3. O que a fundação entrega

| Área | Itens |
|---|---|
| `common` | `RegraNegocioException`, `ErrosDoBanco`, `TratadorGlobalDeExcecoes` (telas: volta à anterior com toast), `TratadorJsonDeExcecoes` (endpoints `@RestController`: status HTTP e JSON) |
| `funcionario` | `Perfil`, `Funcionario`, `FuncionarioRepository`, `UsuarioLogado`, `FuncionarioUserDetailsService` |
| `config` | `Tela` (fonte única de rotas, perfis e menu), `MenuService`, `LayoutAdvice`, `SegurancaConfig`, `RedirecionamentoPorPerfilHandler`, `HomeController`, `LoginController` |
| Templates | `fragments/layout`, `base`, `componentes`, `erro`, `login`, `error/403, 404, 4xx, 5xx` |
| Estáticos | `snackflow.css`, `snackflow.js`, `tailwind-config.js` |
| Ambiente | `application.properties`, `docker-compose.yml` com healthcheck, `.editorconfig` |
| Documentação | `GUIA-DO-MODULO.md`, `README.md`, `CONTRIBUTING.md` |

Pastas de módulo preparadas (sem classes vazias): `cliente`, `cozinha`, `estoque`, `pedido`, `produto`, `relatorio`.

## 4. Decisões tomadas pelo responsável do projeto

1. **Fluxo:** equipe de 4 integrantes; cada um recebe um prompt pronto, gera o seu módulo, abre PR para a `main` e faz o
   merge quando o CI fica verde; se o CI falhar, corrige no próprio PR (`CONTRIBUTING.md`, seção 1). Não se usam issues. Os números 1 a 4 do planejamento são só organização dos módulos.
2. **Contrato PDV ↔ clientes** (guia, seção 14): decidido. Os endpoints `/clientes/*` ainda não existem; o módulo `cliente`
   os implementa e o PDV os consome sem depender de classes Java do outro módulo.
3. **Tratador JSON** (`TratadorJsonDeExcecoes`): aprovado e em uso na `main`.
4. **Venda:** existe quando o pedido entra em EM_PREPARO; contam EM_PREPARO, PRONTO e ENTREGUE; ABERTO nunca conta;
   CANCELADO aparece em coluna própria nos relatórios.
5. **Falha da procedure na confirmação do pedido:** o SQL faz `ROLLBACK` e relança o erro, então o pedido continua ABERTO.
   O PDV o cancela em nova chamada à procedure (ABERTO para CANCELADO). Vale o SQL, não a frase antiga do guia.
6. **Relatórios:** dois controllers independentes (`/relatorios` para vendas e `/relatorios/estoque` para estoque).
7. **Cadastro de ingredientes** pertence ao módulo `estoque`.
8. **Cozinha:** recarrega a página a cada 15 segundos, pausando com a aba oculta.

## 5. Ainda não verificado

- **Navegador:** não há registro de verificação visual no navegador (login e menu dos 4 perfis, layout, toasts, modal,
  spinner, tela estreita). As telas de exemplo (`/teste/layout`, `/teste/formulario`) existem só em `src/test` e **não sobem
  com `spring-boot:run`**; a verificação visual acontece nas primeiras telas dos módulos. Roteiro: `./mvnw spring-boot:run`,
  `http://localhost:8080`, senha `snackflow123`; `admin` e `ana.caixa` caem em `/pdv`, `marcos.cozinha` em `/cozinha`,
  `julia.estoque` em `/estoque`, `roberto.antigo` é recusado; o menu mostra só as rotas do perfil (ADMIN: 6) e URL de outro
  perfil mostra a página 403; em ~375 px o menu recolhe sem rolagem horizontal; sair pelo botão volta a `/login?saiu`.
- **Docker Compose:** `docker compose up -d` e `docker compose ps` até `healthy`; conferir 5 linhas com
  `docker exec snackflow-mysql mysql -uroot -proot lanchonete -e "SELECT login, perfil, ativo FROM funcionarios;"`. Só
  `docker compose down -v` apaga o banco.
- **Tailwind, Lucide e Inter** vêm de CDN; sem internet o visual degrada.
- **Records como `th:object`:** não comprovado; usar classe com getters e setters nos `Form`. Receita para validar: copiar
  `TelaDeFormularioTesteController` com um record e checar, no `ComponentesIntegracaoTest`, valor mantido e `sf-invalido`.
- **Equipe de 4 integrantes** junto ao professor: confirmar, se ainda não foi feito.

## 6. Limitações conhecidas

1. `Tela` centraliza rota, perfis e menu; `SegurancaConfig` e `MenuService` leem dela. Rota nova = uma linha.
2. Telas: o tratador global **redireciona**. Endpoints `@RestController`: o tratador JSON responde com status e JSON. Um
   `@Controller` com `@ResponseBody` não é coberto pelo tratador JSON.
3. O CSS dos componentes é CSS puro (`sf-*`), independente do Tailwind CDN.
4. `Funcionario` só expõe `setAtivo`. O CRUD de funcionários exigirá ampliá-la sem quebrar a autenticação.
5. `Tela.apareceNoMenu()` só é chamado por testes (o `MenuService` usa `tela.secao()` diretamente); inofensivo, a decidir
   depois se passa a ser usado ou é removido.
6. Achados para quem implementar clientes: CPF repetido chega como SQLSTATE 23000 (não 45000), então sem tratamento no
   Service vira 500; o trigger devolve `CPF invalido` sem acento, por isso validar na aplicação antes; o banco guarda o CPF
   com 11 dígitos, e a API responde só dígitos.

## 7. Próximos passos

1. Cada integrante segue seu prompt e abre PR para a `main`, em qualquer ordem (ver `docs/GUIA-DO-MODULO.md`, seção 13).
2. Cada integrante faz o merge do próprio PR com o CI verde, corrigindo na mesma branch se falhar.
3. Ao receber os primeiros módulos, fazer a verificação visual da seção 5 e atualizar este arquivo.
