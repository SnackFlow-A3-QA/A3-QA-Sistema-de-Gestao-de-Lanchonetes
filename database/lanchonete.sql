-- =====================================================================
-- Sistema de Gestão de Lanchonetes - Script de criação (MySQL 8.0.16+)
-- Compatível com MySQL Workbench (Database > Reverse Engineer para o DER)
--
-- Versão revisada. Melhorias em relação à versão anterior:
--   1. valor_total do pedido não pode mais ser alterado manualmente
--      (só aceita o valor igual à soma real dos itens).
--   2. Pedido só pode ser ENTREGUE se tiver ao menos um item.
--   3. Movimentações automáticas passam a registrar id_funcionario
--      (buscado no pedido).
--   4. Cancelamento devolve o saldo LÍQUIDO efetivamente baixado
--      (lido de movimentacoes_estoque), não só os itens atuais.
--   5. Coerência entre tipo / motivo / id_pedido nas movimentações.
--   6. Procedure sp_criar_pedido: pedido + itens em uma única transação.
--
-- Decisões mantidas:
--   - Saldo de estoque continua calculado por movimentações (VIEW).
--   - Progressão de status (pular etapas) fica a cargo da aplicação.
--   - Pagamento obrigatório na entrega: opcional (bloco comentado).
--   - Tabela de pagamentos: evolução futura.
-- =====================================================================

DROP DATABASE IF EXISTS lanchonete;
CREATE DATABASE lanchonete
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE lanchonete;

-- ---------------------------------------------------------------------
-- CATEGORIAS
-- ---------------------------------------------------------------------
CREATE TABLE categorias (
  id_categoria  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nome          VARCHAR(50)  NOT NULL,
  PRIMARY KEY (id_categoria),
  UNIQUE KEY uk_categorias_nome (nome)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- PRODUTOS
-- (o saldo de estoque é calculado a partir de movimentacoes_estoque)
-- ---------------------------------------------------------------------
CREATE TABLE produtos (
  id_produto      INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  nome            VARCHAR(100)  NOT NULL,
  descricao       VARCHAR(255)  NULL,
  id_categoria    INT UNSIGNED  NOT NULL,
  preco           DECIMAL(10,2) NOT NULL,
  estoque_minimo  INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT 'Ponto de reposição: só gera alerta, não bloqueia venda',
  ativo           TINYINT(1)    NOT NULL DEFAULT 1,
  PRIMARY KEY (id_produto),
  UNIQUE KEY uk_produtos_nome (nome),
  KEY idx_produtos_categoria (id_categoria),
  CONSTRAINT fk_produtos_categoria
    FOREIGN KEY (id_categoria) REFERENCES categorias (id_categoria)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT ck_produtos_preco CHECK (preco >= 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- CLIENTES
-- ---------------------------------------------------------------------
CREATE TABLE clientes (
  id_cliente     INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nome           VARCHAR(100) NOT NULL,
  cpf            CHAR(11)     NULL,            -- somente dígitos; NULL = sem CPF
  telefone       VARCHAR(20)  NULL,
  email          VARCHAR(100) NULL,
  data_cadastro  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ativo          TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (id_cliente),
  UNIQUE KEY uk_clientes_cpf (cpf),            -- UNIQUE permite vários NULL
  KEY idx_clientes_nome (nome),
  CONSTRAINT ck_clientes_cpf CHECK (cpf IS NULL OR cpf REGEXP '^[0-9]{11}$')
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- FUNCIONARIOS
-- senha_hash: armazenar hash (bcrypt/Argon2) gerado pela aplicação,
-- nunca a senha em texto puro.
-- ---------------------------------------------------------------------
CREATE TABLE funcionarios (
  id_funcionario  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nome            VARCHAR(100) NOT NULL,
  cargo           VARCHAR(50)  NOT NULL,
  perfil          ENUM('ADMIN','CAIXA','COZINHA') NOT NULL DEFAULT 'CAIXA',
  login           VARCHAR(50)  NOT NULL,
  senha_hash      VARCHAR(255) NOT NULL,
  ativo           TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (id_funcionario),
  UNIQUE KEY uk_funcionarios_login (login)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- PEDIDOS
-- id_cliente NULL = venda de balcão sem cadastro
-- valor_total é controlado pelos triggers de itens_pedido
-- ---------------------------------------------------------------------
CREATE TABLE pedidos (
  id_pedido        INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_cliente       INT UNSIGNED  NULL,
  id_funcionario   INT UNSIGNED  NOT NULL,
  data_hora        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status           ENUM('ABERTO','EM_PREPARO','PRONTO','ENTREGUE','CANCELADO')
                   NOT NULL DEFAULT 'ABERTO',
  forma_pagamento  ENUM('DINHEIRO','CREDITO','DEBITO','PIX','OUTRO') NULL,
  valor_total      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  observacao       VARCHAR(255)  NULL,
  PRIMARY KEY (id_pedido),
  KEY idx_pedidos_cliente (id_cliente),
  KEY idx_pedidos_funcionario (id_funcionario),
  KEY idx_pedidos_data (data_hora),
  KEY idx_pedidos_status (status),
  CONSTRAINT fk_pedidos_cliente
    FOREIGN KEY (id_cliente) REFERENCES clientes (id_cliente)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_pedidos_funcionario
    FOREIGN KEY (id_funcionario) REFERENCES funcionarios (id_funcionario)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT ck_pedidos_total CHECK (valor_total >= 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- ITENS_PEDIDO
-- preco_unitario guarda o preço no momento da venda (histórico)
-- ---------------------------------------------------------------------
CREATE TABLE itens_pedido (
  id_item         INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_pedido       INT UNSIGNED  NOT NULL,
  id_produto      INT UNSIGNED  NOT NULL,
  quantidade      INT UNSIGNED  NOT NULL COMMENT 'Unidades vendidas deste produto no pedido',
  preco_unitario  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Preço unitário NO MOMENTO da venda (0 = copia produtos.preco)',
  subtotal        DECIMAL(10,2) GENERATED ALWAYS AS (quantidade * preco_unitario) STORED,
  PRIMARY KEY (id_item),
  UNIQUE KEY uk_item_pedido_produto (id_pedido, id_produto),
  KEY idx_itens_produto (id_produto),
  CONSTRAINT fk_itens_pedido
    FOREIGN KEY (id_pedido) REFERENCES pedidos (id_pedido)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_itens_produto
    FOREIGN KEY (id_produto) REFERENCES produtos (id_produto)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT ck_itens_qtd   CHECK (quantidade > 0),
  CONSTRAINT ck_itens_preco CHECK (preco_unitario >= 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- MOVIMENTACOES_ESTOQUE
-- tipo ENTRADA soma, SAIDA subtrai. id_pedido NULL em compras/ajustes.
-- A coerência tipo x motivo x id_pedido é validada em trg_mov_bi
-- (um CHECK não pode usar id_pedido porque ele participa de uma FK com
--  ON DELETE SET NULL).
-- ---------------------------------------------------------------------
CREATE TABLE movimentacoes_estoque (
  id_movimentacao  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_produto       INT UNSIGNED NOT NULL,
  id_funcionario   INT UNSIGNED NULL,
  id_pedido        INT UNSIGNED NULL,
  tipo             ENUM('ENTRADA','SAIDA') NOT NULL,
  motivo           ENUM('COMPRA','VENDA','PERDA','AJUSTE','CANCELAMENTO') NOT NULL,
  quantidade       INT UNSIGNED NOT NULL COMMENT 'Unidades movimentadas NESTE lançamento (não é o saldo)',
  data_hora        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  observacao       VARCHAR(255) NULL,
  PRIMARY KEY (id_movimentacao),
  KEY idx_mov_produto (id_produto),
  KEY idx_mov_funcionario (id_funcionario),
  KEY idx_mov_pedido (id_pedido),
  KEY idx_mov_data (data_hora),
  CONSTRAINT fk_mov_produto
    FOREIGN KEY (id_produto) REFERENCES produtos (id_produto)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_mov_funcionario
    FOREIGN KEY (id_funcionario) REFERENCES funcionarios (id_funcionario)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_mov_pedido
    FOREIGN KEY (id_pedido) REFERENCES pedidos (id_pedido)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT ck_mov_qtd CHECK (quantidade > 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- VIEW: saldo atual de estoque por produto + alerta de estoque baixo
-- ---------------------------------------------------------------------
CREATE VIEW vw_estoque_atual AS
SELECT
  p.id_produto,
  p.nome,
  c.nome AS categoria,
  COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN CAST(m.quantidade AS SIGNED)
                    WHEN m.tipo = 'SAIDA'   THEN -CAST(m.quantidade AS SIGNED) END), 0) AS saldo,
  p.estoque_minimo,
  COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN CAST(m.quantidade AS SIGNED)
                    WHEN m.tipo = 'SAIDA'   THEN -CAST(m.quantidade AS SIGNED) END), 0) < p.estoque_minimo AS estoque_baixo
FROM produtos p
JOIN categorias c ON c.id_categoria = p.id_categoria
LEFT JOIN movimentacoes_estoque m ON m.id_produto = p.id_produto
GROUP BY p.id_produto, p.nome, c.nome, p.estoque_minimo;

-- ---------------------------------------------------------------------
-- FUNÇÃO: validação de CPF (dígitos verificadores, módulo 11)
-- Recebe apenas dígitos (a aplicação deve remover pontos e traço).
-- ---------------------------------------------------------------------
DELIMITER $$

CREATE FUNCTION fn_cpf_valido(p_cpf VARCHAR(20))
RETURNS TINYINT(1)
DETERMINISTIC NO SQL
BEGIN
  DECLARE i     INT DEFAULT 1;
  DECLARE soma  INT DEFAULT 0;
  DECLARE resto INT;
  DECLARE dv1   INT;
  DECLARE dv2   INT;

  -- 11 dígitos e não pode ser sequência repetida (000..., 111..., etc.)
  IF p_cpf IS NULL OR p_cpf NOT REGEXP '^[0-9]{11}$' THEN RETURN 0; END IF;
  IF p_cpf = REPEAT(LEFT(p_cpf, 1), 11) THEN RETURN 0; END IF;

  -- 1º dígito: pesos 10..2 sobre os 9 primeiros dígitos
  WHILE i <= 9 DO
    SET soma = soma + CAST(SUBSTRING(p_cpf, i, 1) AS UNSIGNED) * (11 - i);
    SET i = i + 1;
  END WHILE;
  SET resto = soma MOD 11;
  SET dv1 = IF(resto < 2, 0, 11 - resto);
  IF dv1 <> CAST(SUBSTRING(p_cpf, 10, 1) AS UNSIGNED) THEN RETURN 0; END IF;

  -- 2º dígito: pesos 11..2 sobre os 10 primeiros dígitos
  SET i = 1;
  SET soma = 0;
  WHILE i <= 10 DO
    SET soma = soma + CAST(SUBSTRING(p_cpf, i, 1) AS UNSIGNED) * (12 - i);
    SET i = i + 1;
  END WHILE;
  SET resto = soma MOD 11;
  SET dv2 = IF(resto < 2, 0, 11 - resto);
  IF dv2 <> CAST(SUBSTRING(p_cpf, 11, 1) AS UNSIGNED) THEN RETURN 0; END IF;

  RETURN 1;
END$$

-- ---------------------------------------------------------------------
-- TRIGGERS: CLIENTES (CPF válido)
-- CHECK do MySQL não aceita funções próprias, por isso usamos trigger.
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_clientes_bi BEFORE INSERT ON clientes
FOR EACH ROW
BEGIN
  IF NEW.cpf IS NOT NULL AND fn_cpf_valido(NEW.cpf) = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CPF invalido';
  END IF;
END$$

CREATE TRIGGER trg_clientes_bu BEFORE UPDATE ON clientes
FOR EACH ROW
BEGIN
  IF NEW.cpf IS NOT NULL AND fn_cpf_valido(NEW.cpf) = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CPF invalido';
  END IF;
END$$

-- ---------------------------------------------------------------------
-- TRIGGER: MOVIMENTAÇÕES DE ESTOQUE
-- (a) Integridade semântica: tipo, motivo e id_pedido precisam combinar.
--       COMPRA        -> ENTRADA, sem pedido
--       VENDA         -> SAIDA,   com pedido
--       CANCELAMENTO  -> ENTRADA, com pedido
--       PERDA         -> SAIDA
--       AJUSTE        -> ENTRADA ou SAIDA (pedido opcional)
-- (b) Nunca deixa o saldo ficar negativo (vale para qualquer saída).
--     O FOR UPDATE trava a linha do produto: dois caixas vendendo o
--     último item ao mesmo tempo são atendidos em fila.
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_mov_bi BEFORE INSERT ON movimentacoes_estoque
FOR EACH ROW
BEGIN
  DECLARE v_lock  INT;
  DECLARE v_saldo INT DEFAULT 0;
  DECLARE v_msg   VARCHAR(128);

  -- (a) coerência tipo x motivo x pedido
  IF NEW.motivo = 'COMPRA' AND (NEW.tipo <> 'ENTRADA' OR NEW.id_pedido IS NOT NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Motivo COMPRA exige tipo ENTRADA e nao pode ter pedido';
  ELSEIF NEW.motivo = 'VENDA' AND (NEW.tipo <> 'SAIDA' OR NEW.id_pedido IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Motivo VENDA exige tipo SAIDA e um pedido associado';
  ELSEIF NEW.motivo = 'CANCELAMENTO' AND (NEW.tipo <> 'ENTRADA' OR NEW.id_pedido IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Motivo CANCELAMENTO exige tipo ENTRADA e um pedido associado';
  ELSEIF NEW.motivo = 'PERDA' AND NEW.tipo <> 'SAIDA' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Motivo PERDA exige tipo SAIDA';
  END IF;

  -- (b) proteção contra estoque negativo
  IF NEW.tipo = 'SAIDA' THEN
    SELECT id_produto INTO v_lock
      FROM produtos WHERE id_produto = NEW.id_produto FOR UPDATE;

    SELECT COALESCE(SUM(CASE WHEN tipo = 'ENTRADA' THEN CAST(quantidade AS SIGNED)
                             ELSE -CAST(quantidade AS SIGNED) END), 0)
      INTO v_saldo
      FROM movimentacoes_estoque WHERE id_produto = NEW.id_produto;

    IF v_saldo < CAST(NEW.quantidade AS SIGNED) THEN
      SET v_msg = CONCAT('Estoque insuficiente (produto ', NEW.id_produto,
                         '): saldo ', v_saldo, ', solicitado ', NEW.quantidade);
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;
  END IF;
END$$

-- ---------------------------------------------------------------------
-- TRIGGERS: ITENS_PEDIDO
-- Regras: só altera itens de pedido ABERTO; produto inativo não vende;
-- preço vem do cadastro se não informado; total e estoque acompanham;
-- toda movimentação gerada registra o funcionário do pedido.
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_itens_bi BEFORE INSERT ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_status VARCHAR(20);
  DECLARE v_preco  DECIMAL(10,2);
  DECLARE v_ativo  TINYINT(1);

  SELECT status INTO v_status FROM pedidos WHERE id_pedido = NEW.id_pedido;
  IF v_status <> 'ABERTO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido nao esta ABERTO: itens nao podem ser alterados';
  END IF;

  SELECT preco, ativo INTO v_preco, v_ativo FROM produtos WHERE id_produto = NEW.id_produto;
  IF v_ativo = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto inativo nao pode ser vendido';
  END IF;

  IF NEW.preco_unitario IS NULL OR NEW.preco_unitario = 0 THEN
    SET NEW.preco_unitario = v_preco;
  END IF;
END$$

CREATE TRIGGER trg_itens_ai AFTER INSERT ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_func INT UNSIGNED;
  SELECT id_funcionario INTO v_func FROM pedidos WHERE id_pedido = NEW.id_pedido;

  -- a baixa de estoque é validada por trg_mov_bi (falhou = item não é gravado)
  INSERT INTO movimentacoes_estoque (id_produto, id_funcionario, id_pedido, tipo, motivo, quantidade)
  VALUES (NEW.id_produto, v_func, NEW.id_pedido, 'SAIDA', 'VENDA', NEW.quantidade);

  UPDATE pedidos
     SET valor_total = (SELECT COALESCE(SUM(subtotal),0) FROM itens_pedido WHERE id_pedido = NEW.id_pedido)
   WHERE id_pedido = NEW.id_pedido;
END$$

CREATE TRIGGER trg_itens_bu BEFORE UPDATE ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_status VARCHAR(20);

  IF NEW.id_pedido <> OLD.id_pedido OR NEW.id_produto <> OLD.id_produto THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Nao e permitido trocar pedido/produto do item: exclua e insira novamente';
  END IF;

  SELECT status INTO v_status FROM pedidos WHERE id_pedido = OLD.id_pedido;
  IF v_status <> 'ABERTO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido nao esta ABERTO: itens nao podem ser alterados';
  END IF;
END$$

CREATE TRIGGER trg_itens_au AFTER UPDATE ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_func INT UNSIGNED;
  SELECT id_funcionario INTO v_func FROM pedidos WHERE id_pedido = NEW.id_pedido;

  IF NEW.quantidade > OLD.quantidade THEN
    INSERT INTO movimentacoes_estoque (id_produto, id_funcionario, id_pedido, tipo, motivo, quantidade)
    VALUES (NEW.id_produto, v_func, NEW.id_pedido, 'SAIDA', 'VENDA', NEW.quantidade - OLD.quantidade);
  ELSEIF NEW.quantidade < OLD.quantidade THEN
    INSERT INTO movimentacoes_estoque (id_produto, id_funcionario, id_pedido, tipo, motivo, quantidade)
    VALUES (NEW.id_produto, v_func, NEW.id_pedido, 'ENTRADA', 'AJUSTE', OLD.quantidade - NEW.quantidade);
  END IF;

  UPDATE pedidos
     SET valor_total = (SELECT COALESCE(SUM(subtotal),0) FROM itens_pedido WHERE id_pedido = NEW.id_pedido)
   WHERE id_pedido = NEW.id_pedido;
END$$

CREATE TRIGGER trg_itens_bd BEFORE DELETE ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_status VARCHAR(20);
  SELECT status INTO v_status FROM pedidos WHERE id_pedido = OLD.id_pedido;
  IF v_status <> 'ABERTO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido nao esta ABERTO: itens nao podem ser removidos';
  END IF;
END$$

CREATE TRIGGER trg_itens_ad AFTER DELETE ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_func INT UNSIGNED;
  SELECT id_funcionario INTO v_func FROM pedidos WHERE id_pedido = OLD.id_pedido;

  INSERT INTO movimentacoes_estoque (id_produto, id_funcionario, id_pedido, tipo, motivo, quantidade)
  VALUES (OLD.id_produto, v_func, OLD.id_pedido, 'ENTRADA', 'AJUSTE', OLD.quantidade);

  UPDATE pedidos
     SET valor_total = (SELECT COALESCE(SUM(subtotal),0) FROM itens_pedido WHERE id_pedido = OLD.id_pedido)
   WHERE id_pedido = OLD.id_pedido;
END$$

-- ---------------------------------------------------------------------
-- TRIGGERS: PEDIDOS
-- Regras:
--   * valor_total só aceita o valor igual à soma real dos itens
--     (impede UPDATE manual divergente);
--   * ENTREGUE e CANCELADO são estados finais;
--   * o status não pode voltar para etapa anterior
--     (pular etapas é permitido: decisão deixada para a aplicação);
--   * pedido só pode ser ENTREGUE se tiver ao menos um item;
--   * cancelar devolve o saldo líquido efetivamente baixado;
--   * pedido não pode ser excluído.
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_pedidos_bu BEFORE UPDATE ON pedidos
FOR EACH ROW
BEGIN
  DECLARE v_soma  DECIMAL(10,2);
  DECLARE v_itens INT DEFAULT 0;

  -- 1) valor_total não pode ser alterado manualmente
  IF NEW.valor_total <> OLD.valor_total THEN
    SELECT COALESCE(SUM(subtotal), 0) INTO v_soma
      FROM itens_pedido WHERE id_pedido = OLD.id_pedido;
    IF NEW.valor_total <> v_soma THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'valor_total e calculado pelos itens e nao pode ser alterado manualmente';
    END IF;
  END IF;

  -- 2) regras de status
  IF NEW.status <> OLD.status THEN
    IF OLD.status IN ('ENTREGUE','CANCELADO') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido finalizado: o status nao pode mais ser alterado';
    ELSEIF NEW.status <> 'CANCELADO' AND (NEW.status + 0) < (OLD.status + 0) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O status do pedido nao pode voltar para uma etapa anterior';
    END IF;

    -- 3) não finaliza pedido vazio
    IF NEW.status = 'ENTREGUE' THEN
      SELECT COUNT(*) INTO v_itens FROM itens_pedido WHERE id_pedido = OLD.id_pedido;
      IF v_itens = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido sem itens nao pode ser ENTREGUE';
      END IF;

      -- OPCIONAL (depende da regra comercial): exigir forma de pagamento.
      -- Descomente para ativar.
      -- IF NEW.forma_pagamento IS NULL THEN
      --   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe a forma de pagamento antes de entregar';
      -- END IF;
    END IF;
  END IF;
END$$

-- Cancelamento: devolve, por produto, o saldo LÍQUIDO que o pedido ainda
-- mantém baixado, calculado a partir das movimentações reais do pedido
-- (VENDA e AJUSTE), e não apenas das quantidades atuais em itens_pedido.
CREATE TRIGGER trg_pedidos_au AFTER UPDATE ON pedidos
FOR EACH ROW
BEGIN
  IF NEW.status = 'CANCELADO' AND OLD.status <> 'CANCELADO' THEN
    INSERT INTO movimentacoes_estoque
           (id_produto, id_funcionario, id_pedido, tipo, motivo, quantidade, observacao)
    SELECT m.id_produto,
           NEW.id_funcionario,
           NEW.id_pedido,
           'ENTRADA',
           'CANCELAMENTO',
           SUM(CASE WHEN m.tipo = 'SAIDA' THEN CAST(m.quantidade AS SIGNED)
                    ELSE -CAST(m.quantidade AS SIGNED) END),
           'Devolucao por cancelamento do pedido'
      FROM movimentacoes_estoque m
     WHERE m.id_pedido = NEW.id_pedido
       AND m.motivo IN ('VENDA','AJUSTE')
     GROUP BY m.id_produto
    HAVING SUM(CASE WHEN m.tipo = 'SAIDA' THEN CAST(m.quantidade AS SIGNED)
                    ELSE -CAST(m.quantidade AS SIGNED) END) > 0;
  END IF;
END$$

CREATE TRIGGER trg_pedidos_bd BEFORE DELETE ON pedidos
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedidos nao podem ser excluidos: use o status CANCELADO';
END$$

-- ---------------------------------------------------------------------
-- PROCEDURE: criar pedido completo em UMA transação
-- Cria o pedido, insere todos os itens e confirma. Se qualquer item
-- falhar (estoque insuficiente, produto inativo...), tudo é desfeito e
-- nenhum pedido pela metade fica no banco.
--
-- p_itens: JSON no formato
--   [{"id_produto":1,"quantidade":3},{"id_produto":2,"quantidade":1}]
-- Obs.: START TRANSACTION confirma implicitamente qualquer transação
-- que já esteja aberta na mesma conexão.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_criar_pedido(
  IN  p_id_funcionario  INT UNSIGNED,
  IN  p_id_cliente      INT UNSIGNED,       -- NULL = venda de balcão
  IN  p_forma_pagamento VARCHAR(20),        -- NULL ou DINHEIRO/CREDITO/DEBITO/PIX/OUTRO
  IN  p_itens           JSON,
  OUT p_id_pedido       INT UNSIGNED
)
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    SET p_id_pedido = NULL;
    RESIGNAL;
  END;

  IF p_itens IS NULL OR JSON_TYPE(p_itens) <> 'ARRAY' OR JSON_LENGTH(p_itens) = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe ao menos um item no pedido (JSON array)';
  END IF;

  START TRANSACTION;

  INSERT INTO pedidos (id_cliente, id_funcionario, forma_pagamento)
  VALUES (p_id_cliente, p_id_funcionario, p_forma_pagamento);

  SET p_id_pedido = LAST_INSERT_ID();

  -- ORDER BY id_produto: todas as sessões travam os produtos na mesma
  -- ordem, o que reduz o risco de deadlock entre dois caixas.
  INSERT INTO itens_pedido (id_pedido, id_produto, quantidade)
  SELECT p_id_pedido, j.id_produto, j.quantidade
    FROM JSON_TABLE(p_itens, '$[*]' COLUMNS (
           id_produto INT UNSIGNED PATH '$.id_produto',
           quantidade INT UNSIGNED PATH '$.quantidade'
         )) AS j
   ORDER BY j.id_produto;

  COMMIT;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- DADOS INICIAIS
-- ---------------------------------------------------------------------
INSERT INTO categorias (nome) VALUES
  ('Lanches'), ('Bebidas'), ('Porções'), ('Sobremesas'), ('Combos');

-- ---------------------------------------------------------------------
-- ROTEIRO DE TESTE (descomente e execute aos poucos)
-- ---------------------------------------------------------------------
-- -- CPF
-- SELECT fn_cpf_valido('52998224725');   -- 1 (válido)
-- SELECT fn_cpf_valido('12345678900');   -- 0 (dígito verificador errado)
-- INSERT INTO clientes (nome, cpf) VALUES ('Teste', '12345678900');  -- ERRO: CPF invalido
--
-- -- Cadastros e estoque inicial
-- INSERT INTO funcionarios (nome, cargo, perfil, login, senha_hash) VALUES ('Teste','Caixa','CAIXA','teste','hash');
-- INSERT INTO produtos (nome, id_categoria, preco, estoque_minimo) VALUES ('Coca 350ml', 2, 6.00, 10);
-- INSERT INTO movimentacoes_estoque (id_produto, tipo, motivo, quantidade) VALUES (1,'ENTRADA','COMPRA',20);
--
-- -- Venda e estoque
-- INSERT INTO pedidos (id_funcionario) VALUES (1);
-- INSERT INTO itens_pedido (id_pedido, id_produto, quantidade) VALUES (1,1,15);  -- OK, saldo 5, abaixo do mínimo
-- SELECT * FROM vw_estoque_atual;                                                 -- estoque_baixo = 1
-- INSERT INTO itens_pedido (id_pedido, id_produto, quantidade) VALUES (1,1,6);   -- ERRO: Estoque insuficiente
--
-- -- Rastreabilidade: a saída automática agora traz o funcionário
-- SELECT id_movimentacao, id_produto, id_funcionario, id_pedido, tipo, motivo, quantidade
--   FROM movimentacoes_estoque;                                                   -- VENDA com id_funcionario = 1
--
-- -- Total do pedido protegido
-- UPDATE pedidos SET valor_total = 999.99 WHERE id_pedido = 1;                    -- ERRO: valor_total e calculado pelos itens
--
-- -- Coerência das movimentações
-- INSERT INTO movimentacoes_estoque (id_produto, tipo, motivo, quantidade) VALUES (1,'SAIDA','VENDA',1);        -- ERRO: VENDA exige pedido
-- INSERT INTO movimentacoes_estoque (id_produto, tipo, motivo, quantidade) VALUES (1,'SAIDA','COMPRA',1);       -- ERRO: COMPRA exige ENTRADA
--
-- -- Pedido vazio não pode ser entregue
-- INSERT INTO pedidos (id_funcionario) VALUES (1);                                -- pedido 2, sem itens
-- UPDATE pedidos SET status = 'ENTREGUE' WHERE id_pedido = 2;                     -- ERRO: Pedido sem itens
--
-- -- Cancelamento devolve o saldo líquido
-- UPDATE itens_pedido SET quantidade = 10 WHERE id_pedido = 1 AND id_produto = 1; -- devolve 5 (AJUSTE)
-- UPDATE pedidos SET status = 'CANCELADO' WHERE id_pedido = 1;                    -- devolve 10 (CANCELAMENTO)
-- SELECT * FROM vw_estoque_atual;                                                 -- saldo volta a 20
--
-- -- Pedido completo em uma transação
-- CALL sp_criar_pedido(1, NULL, 'PIX', '[{"id_produto":1,"quantidade":3}]', @novo_pedido);
-- SELECT @novo_pedido;
-- CALL sp_criar_pedido(1, NULL, 'PIX', '[{"id_produto":1,"quantidade":999}]', @x);  -- ERRO e nada fica gravado
