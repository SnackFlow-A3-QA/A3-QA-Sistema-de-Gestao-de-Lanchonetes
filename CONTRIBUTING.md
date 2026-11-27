# Guia de contribuição

Como se trabalha no SnackFlow. A equipe tem **4 integrantes**, cada um com os seus módulos. Cada integrante abre o PR do seu módulo e faz o
merge na `main` quando o CI estiver verde. Leia antes de começar a codar.

## 1. Fluxo de trabalho (equipe de 4, cada um faz o merge do seu PR)

1. Crie uma **branch** a partir da `main` atualizada (`git pull` antes): `feature/<modulo>-<assunto>`, `fix/...`, `test/...`.
2. Escreva o código **e o teste juntos** (veja a seção 2) e faça commits pequenos, com a sua conta pessoal.
3. Rode `./mvnw verify` na sua máquina (Docker aberto). Só abra o PR com o build verde.
4. Abra o **Pull Request para a `main`** preenchendo o template.
5. Aguarde o **CI verde** (testes + JaCoCo + SonarCloud com *quality gate* aprovado) e faça o merge. Se o CI falhar,
   corrija na mesma branch e envie de novo: o próprio PR é atualizado.

Ninguém faz push direto na `main`: a **regra de proteção da branch** exige o PR e as verificações do CI, e é ela que
bloqueia o merge enquanto o CI ou o Sonar não estiverem aprovados. (A proteção da `main` e o `SONAR_TOKEN` foram
informados como configurados; confira em *Settings → Branches* e *Secrets* se tiver dúvida.)

## 2. Como funcionam os testes

Cada classe de regra de negócio nasce com seu teste, no mesmo pacote, dentro de `src/test/java`.

| Tipo | Exemplo | Ferramentas | O que valida |
|---|---|---|---|
| Unitário | `ProdutoServiceTest` | JUnit 5 + Mockito + AssertJ | Regras de negócio isoladas (repositório simulado) |
| Integração | `ProdutoRepositoryTest` | Spring + Testcontainers (MySQL real) | Consultas, triggers, views e procedure |
| Web | `ProdutoControllerTest` | MockMvc | Rotas, validação de formulário, perfis de acesso |

Regras de teste:

- Todo método público de `Service` tem pelo menos um teste de sucesso e um de falha.
- Padrão **Arrange / Act / Assert**; nomes como `deveRejeitarPrecoNegativo`.
- Prefira testes unitários (rápidos). Use `@SpringBootTest` apenas quando necessário.
- **Nome da classe de teste termina em `Test`.** Classes `*IT` não são executadas pelo Maven.
- Não use banco local nos testes: o Testcontainers sobe um MySQL descartável (o Docker precisa estar aberto) e o reaproveita entre as classes.
- Testes que chamam `sp_alterar_status_pedido` **não podem ser `@Transactional`** (a procedure faz commit). Use dados próprios por teste ou limpeza com `@Sql`.
- O banco de teste já nasce com os dados de exemplo do script; não dependa deles.

## 3. Cobertura e qualidade

- O JaCoCo exige **80% de cobertura de linhas no projeto inteiro**; abaixo disso o `./mvnw verify` falha.
- Para ver o relatório: `./mvnw verify` e abra `target/site/jacoco/index.html`.
- O SonarCloud analisa cada push e PR; o *quality gate* precisa estar aprovado.
- Issues do SonarCloud e issues abertas pelo professor devem ser resolvidas antes da entrega.

## 4. Commits

- Padrão **Conventional Commits**: `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:`, `ci:`.
- Commits pequenos, com uma única finalidade (um teste, uma correção, uma tela).
- Commite com a **sua conta pessoal do GitHub**, apenas os arquivos que você escreveu (o edital avalia a autoria).
- Commits de README, `pom.xml` e configurações não contam na avaliação de código do edital.
- Mudanças em `database/lanchonete.sql`, `pom.xml`, `ci.yml`, segurança e layout são decisões deliberadas: faça-as em
  commits e PRs separados e descreva o motivo no PR, porque afetam todos os módulos. Integrantes não alteram esses
  arquivos sem alinhar antes com o responsável do projeto.

## 5. Organização dos módulos

Cada módulo é um pacote em `src/main/java/br/com/snackflow`; trabalhar um pacote por vez mantém os PRs pequenos.

Os módulos são independentes: cada PR toca só os arquivos do próprio módulo e pode entrar na `main` em qualquer ordem.
Quem trabalha em cada um:

| Responsável | Módulos (pacotes) |
|---|---|
| Lucas N. | `pedido` (PDV), `cozinha` |
| Guilherme H. | `funcionario`, `cliente`, `config` (segurança e layout), pipeline de CI e Sonar |
| Rafael T. | `produto` (cardápio), `relatorio` (vendas, `GET /relatorios`) |
| Rodrigo F. | `estoque`, `relatorio` (estoque, `GET /relatorios/estoque`) |

O módulo `relatorio` tem dois controllers independentes, um de cada responsável, sem classe em comum.

O pacote `common` é compartilhado (exceções, utilitários); alterações nele também seguem o fluxo da seção 1.

Onde criar cada classe, como usar layout e componentes, tratar erros e testar: **[docs/GUIA-DO-MODULO.md](docs/GUIA-DO-MODULO.md)**.
A lista de arquivos compartilhados (alterar só de propósito) está na seção 9 do guia.

## 6. Rodando na sua máquina

```bash
docker compose up -d        # MySQL local, com o script de database/
./mvnw spring-boot:run      # aplicação em http://localhost:8080
./mvnw verify               # testes + cobertura (Docker aberto)
docker compose down -v      # apaga o banco; o próximo "up" recria do zero
```
