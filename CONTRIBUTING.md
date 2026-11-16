# Guia da equipe

Como trabalhamos no SnackFlow. Leia antes de começar a codar.

## 1. Fluxo de trabalho

1. Pegue uma **issue** (ou crie uma com o template) e se atribua.
2. Crie uma branch a partir da `main`: `feature/produto-cadastro`, `fix/cpf-invalido`, `test/estoque-saldo`.
3. Escreva o código **e o teste juntos** (veja a seção 2).
4. Rode `mvn verify` na sua máquina. Só abra o PR com o build verde.
5. Abra o Pull Request preenchendo o template e escreva `Closes #N` para fechar a issue.
6. Um colega aprova, o CI fica verde (testes + JaCoCo + SonarCloud) e então o merge é feito na `main`.

Ninguém faz push direto na `main`.

## 2. Como funcionam os testes

Cada classe de regra de negócio nasce com seu teste, no mesmo pacote, dentro de `src/test/java`.

| Tipo | Exemplo | Ferramentas | O que valida |
|---|---|---|---|
| Unitário | `ProdutoServiceTest` | JUnit 5 + Mockito | Regras de negócio isoladas (repositório simulado) |
| Integração | `ProdutoRepositoryTest` | Spring + Testcontainers (MySQL real) | Consultas, triggers, view e procedure |
| Web | `ProdutoControllerTest` | MockMvc | Rotas, validação de formulário, perfis de acesso |

Regras do grupo:

- Todo método público de `Service` tem pelo menos um teste de sucesso e um de falha.
- Padrão **Arrange / Act / Assert**; nomes como `deveRejeitarPrecoNegativo`.
- Prefira testes unitários (rápidos). Use `@SpringBootTest` apenas quando necessário.
- **Nome da classe de teste termina em `Test`.** Classes `*IT` não são executadas pelo Maven.
- Não use banco local nos testes: o Testcontainers sobe um MySQL descartável (o Docker precisa estar aberto).

## 3. Cobertura e qualidade

- O JaCoCo exige **80% de cobertura de linhas no projeto inteiro**; abaixo disso o `mvn verify` falha.
- Para ver o relatório: `mvn verify` e abra `target/site/jacoco/index.html`.
- O SonarCloud analisa cada push e PR; o *quality gate* precisa estar aprovado.
- Issues do SonarCloud e issues abertas pelo professor devem ser resolvidas antes da entrega.

## 4. Commits

- Padrão **Conventional Commits**: `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:`, `ci:`.
- Commits pequenos, com uma única finalidade (um teste, uma correção, uma tela).
- Use **a sua conta pessoal do GitHub** e commite apenas os arquivos que você escreveu.
- Commits de README, `pom.xml` e configurações não contam na avaliação individual de código.

## 5. Quem cuida de quê

Cada módulo é um pacote em `src/main/java/br.com.snackflow`. Trabalhar em pacotes diferentes evita conflito de merge.

| Responsável | Módulos (pacotes) |
|---|---|
| (membro 1, líder) | `pedido` |
| (membro 2, DevOps) | `funcionario`, `config` (segurança), pipeline de CI |
| (membro 3) | `produto`, `estoque` |
| (membro 4) | `cliente`, `relatorio` |

O pacote `common` é compartilhado (exceções, utilitários); alterações nele passam por PR com aviso ao grupo.

## 6. Rodando na sua máquina

```bash
docker compose up -d        # MySQL local, com o script de database/
mvn spring-boot:run         # aplicação em http://localhost:8080
mvn verify                  # testes + cobertura (Docker aberto)
```
