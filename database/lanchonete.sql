DROP DATABASE IF EXISTS lanchonete;
CREATE DATABASE lanchonete
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE lanchonete;

-- =====================================================================
-- TABELAS
-- =====================================================================

CREATE TABLE categorias (
  id_categoria  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nome          VARCHAR(50)  NOT NULL,
  PRIMARY KEY (id_categoria),
  UNIQUE KEY uk_categorias_nome (nome)
) ENGINE=InnoDB;

CREATE TABLE ingredientes (
  id_ingrediente  INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  nome            VARCHAR(100)  NOT NULL,
  unidade         ENUM('UN','G','ML') NOT NULL,
  estoque_minimo  DECIMAL(10,3) NOT NULL DEFAULT 0,
  saldo_atual     DECIMAL(14,3) NOT NULL DEFAULT 0,
  custo_atual     DECIMAL(12,4) NOT NULL DEFAULT 0,
  ativo           TINYINT(1)    NOT NULL DEFAULT 1,
  PRIMARY KEY (id_ingrediente),
  UNIQUE KEY uk_ingredientes_nome (nome),
  CONSTRAINT ck_ingredientes_min   CHECK (estoque_minimo >= 0),
  CONSTRAINT ck_ingredientes_saldo CHECK (saldo_atual >= 0),
  CONSTRAINT ck_ingredientes_custo CHECK (custo_atual >= 0)
) ENGINE=InnoDB;

CREATE TABLE produtos (
  id_produto    INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  nome          VARCHAR(100)  NOT NULL,
  descricao     VARCHAR(255)  NULL,
  id_categoria  INT UNSIGNED  NOT NULL,
  preco         DECIMAL(10,2) NOT NULL,
  ativo         TINYINT(1)    NOT NULL DEFAULT 1,
  PRIMARY KEY (id_produto),
  UNIQUE KEY uk_produtos_nome (nome),
  KEY idx_produtos_categoria (id_categoria),
  CONSTRAINT fk_produtos_categoria
    FOREIGN KEY (id_categoria) REFERENCES categorias (id_categoria)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT ck_produtos_preco CHECK (preco >= 0)
) ENGINE=InnoDB;

CREATE TABLE produto_ingredientes (
  id_produto      INT UNSIGNED  NOT NULL,
  id_ingrediente  INT UNSIGNED  NOT NULL,
  quantidade      DECIMAL(10,3) NOT NULL,
  PRIMARY KEY (id_produto, id_ingrediente),
  KEY idx_prodingr_ingrediente (id_ingrediente),
  CONSTRAINT fk_prodingr_produto
    FOREIGN KEY (id_produto) REFERENCES produtos (id_produto)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_prodingr_ingrediente
    FOREIGN KEY (id_ingrediente) REFERENCES ingredientes (id_ingrediente)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT ck_prodingr_qtd CHECK (quantidade > 0)
) ENGINE=InnoDB;

CREATE TABLE produto_opcoes (
  id_opcao         INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_produto       INT UNSIGNED  NOT NULL,
  tipo             ENUM('ADICIONAL','REMOVER') NOT NULL,
  nome             VARCHAR(60)   NOT NULL,
  id_ingrediente   INT UNSIGNED  NOT NULL,
  quantidade       DECIMAL(10,3) NULL,
  preco_adicional  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  ativo            TINYINT(1)    NOT NULL DEFAULT 1,
  PRIMARY KEY (id_opcao),
  UNIQUE KEY uk_opcoes (id_produto, tipo, id_ingrediente),
  KEY idx_opcoes_ingrediente (id_ingrediente),
  CONSTRAINT fk_opcoes_produto
    FOREIGN KEY (id_produto) REFERENCES produtos (id_produto)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_opcoes_ingrediente
    FOREIGN KEY (id_ingrediente) REFERENCES ingredientes (id_ingrediente)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT ck_opcoes_regra CHECK (
       (tipo = 'ADICIONAL' AND quantidade IS NOT NULL AND quantidade > 0)
    OR (tipo = 'REMOVER'   AND quantidade IS NULL AND preco_adicional = 0)),
  CONSTRAINT ck_opcoes_preco CHECK (preco_adicional >= 0)
) ENGINE=InnoDB;

CREATE TABLE clientes (
  id_cliente  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nome        VARCHAR(100) NOT NULL,
  cpf         CHAR(11)     NULL,
  telefone    VARCHAR(20)  NULL,
  ativo       TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (id_cliente),
  UNIQUE KEY uk_clientes_cpf (cpf),
  KEY idx_clientes_nome (nome),
  CONSTRAINT ck_clientes_cpf CHECK (cpf IS NULL OR cpf REGEXP '^[0-9]{11}$')
) ENGINE=InnoDB;

CREATE TABLE funcionarios (
  id_funcionario  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nome            VARCHAR(100) NOT NULL,
  cargo           VARCHAR(50)  NOT NULL,
  perfil          ENUM('CAIXA','COZINHA','ESTOQUE','ADMIN') NOT NULL DEFAULT 'CAIXA',
  login           VARCHAR(50)  NOT NULL,
  senha_hash      VARCHAR(255) NOT NULL,
  ativo           TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (id_funcionario),
  UNIQUE KEY uk_funcionarios_login (login)
) ENGINE=InnoDB;

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

CREATE TABLE itens_pedido (
  id_item         INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_pedido       INT UNSIGNED  NOT NULL,
  id_produto      INT UNSIGNED  NOT NULL,
  quantidade      INT UNSIGNED  NOT NULL,
  preco_unitario  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  preco_opcoes    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  observacao      VARCHAR(255)  NULL,
  subtotal        DECIMAL(10,2) GENERATED ALWAYS AS (quantidade * (preco_unitario + preco_opcoes)) STORED,
  PRIMARY KEY (id_item),
  KEY idx_itens_pedido (id_pedido),
  KEY idx_itens_produto (id_produto),
  CONSTRAINT fk_itens_pedido
    FOREIGN KEY (id_pedido) REFERENCES pedidos (id_pedido)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_itens_produto
    FOREIGN KEY (id_produto) REFERENCES produtos (id_produto)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT ck_itens_qtd    CHECK (quantidade > 0),
  CONSTRAINT ck_itens_preco  CHECK (preco_unitario >= 0),
  CONSTRAINT ck_itens_opcoes CHECK (preco_opcoes >= 0)
) ENGINE=InnoDB;

CREATE TABLE itens_pedido_opcoes (
  id_item          INT UNSIGNED  NOT NULL,
  id_opcao         INT UNSIGNED  NOT NULL,
  preco_adicional  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (id_item, id_opcao),
  KEY idx_iop_opcao (id_opcao),
  CONSTRAINT fk_iop_item
    FOREIGN KEY (id_item) REFERENCES itens_pedido (id_item)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_iop_opcao
    FOREIGN KEY (id_opcao) REFERENCES produto_opcoes (id_opcao)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT ck_iop_preco CHECK (preco_adicional >= 0)
) ENGINE=InnoDB;

CREATE TABLE movimentacoes_estoque (
  id_movimentacao  INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_ingrediente   INT UNSIGNED  NOT NULL,
  id_funcionario   INT UNSIGNED  NOT NULL,
  id_pedido        INT UNSIGNED  NULL,
  tipo             ENUM('ENTRADA','SAIDA') NOT NULL,
  motivo           ENUM('COMPRA','VENDA','CANCELAMENTO','PERDA','AJUSTE') NOT NULL,
  quantidade       DECIMAL(10,3) NOT NULL,
  custo_unitario   DECIMAL(12,4) NULL,
  data_hora        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  observacao       VARCHAR(255)  NULL,
  PRIMARY KEY (id_movimentacao),
  KEY idx_mov_ingrediente (id_ingrediente),
  KEY idx_mov_funcionario (id_funcionario),
  KEY idx_mov_pedido (id_pedido),
  KEY idx_mov_data (data_hora),
  CONSTRAINT fk_mov_ingrediente
    FOREIGN KEY (id_ingrediente) REFERENCES ingredientes (id_ingrediente)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_mov_funcionario
    FOREIGN KEY (id_funcionario) REFERENCES funcionarios (id_funcionario)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_mov_pedido
    FOREIGN KEY (id_pedido) REFERENCES pedidos (id_pedido)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_mov_qtd   CHECK (quantidade > 0),
  CONSTRAINT ck_mov_custo CHECK (custo_unitario IS NULL OR custo_unitario >= 0)
) ENGINE=InnoDB;

-- =====================================================================
-- VIEWS
-- =====================================================================

CREATE VIEW vw_estoque_ingredientes AS
SELECT i.id_ingrediente,
       i.nome,
       i.unidade,
       i.saldo_atual AS saldo,
       i.estoque_minimo,
       i.saldo_atual < i.estoque_minimo AS estoque_baixo,
       i.custo_atual,
       ROUND(i.saldo_atual * i.custo_atual, 2) AS valor_em_estoque,
       i.ativo
  FROM ingredientes i;

CREATE VIEW vw_disponibilidade_produtos AS
SELECT p.id_produto,
       p.nome,
       c.nome AS categoria,
       p.preco,
       p.ativo,
       CAST(COALESCE(FLOOR(MIN(i.saldo_atual / pi.quantidade)), 0) AS SIGNED) AS disponivel
  FROM produtos p
  JOIN categorias c ON c.id_categoria = p.id_categoria
  LEFT JOIN produto_ingredientes pi ON pi.id_produto = p.id_produto
  LEFT JOIN ingredientes i ON i.id_ingrediente = pi.id_ingrediente
 GROUP BY p.id_produto, p.nome, c.nome, p.preco, p.ativo;

CREATE VIEW vw_produtos_custo AS
SELECT t.id_produto,
       t.nome,
       t.preco,
       t.custo,
       t.preco - t.custo AS margem_valor,
       CASE WHEN t.preco > 0 THEN ROUND((t.preco - t.custo) / t.preco * 100, 1) END AS margem_percentual,
       t.custo > t.preco AS abaixo_do_custo,
       t.ativo
  FROM (SELECT p.id_produto, p.nome, p.preco, p.ativo,
               ROUND(COALESCE(SUM(pi.quantidade * i.custo_atual), 0), 2) AS custo
          FROM produtos p
          LEFT JOIN produto_ingredientes pi ON pi.id_produto = p.id_produto
          LEFT JOIN ingredientes i ON i.id_ingrediente = pi.id_ingrediente
         GROUP BY p.id_produto, p.nome, p.preco, p.ativo) t;

CREATE VIEW vw_cozinha AS
SELECT p.id_pedido,
       p.data_hora,
       cl.nome       AS cliente,
       p.observacao  AS obs_pedido,
       pr.nome       AS produto,
       i.quantidade,
       (SELECT GROUP_CONCAT(CONCAT(IF(o.tipo = 'ADICIONAL', '+ ', 'SEM '), o.nome)
                            ORDER BY o.tipo, o.nome SEPARATOR ', ')
          FROM itens_pedido_opcoes io
          JOIN produto_opcoes o ON o.id_opcao = io.id_opcao
         WHERE io.id_item = i.id_item) AS opcoes,
       i.observacao  AS obs_item
  FROM pedidos p
  JOIN itens_pedido i ON i.id_pedido = p.id_pedido
  JOIN produtos pr    ON pr.id_produto = i.id_produto
  LEFT JOIN clientes cl ON cl.id_cliente = p.id_cliente
 WHERE p.status = 'EM_PREPARO'
 ORDER BY p.data_hora, p.id_pedido, i.id_item;

-- =====================================================================
-- FUNCAO, TRIGGERS E PROCEDURE
-- =====================================================================

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

  IF p_cpf IS NULL OR p_cpf NOT REGEXP '^[0-9]{11}$' THEN RETURN 0; END IF;
  IF CHAR_LENGTH(REPLACE(p_cpf, LEFT(p_cpf, 1), '')) = 0 THEN RETURN 0; END IF;

  WHILE i <= 9 DO
    SET soma = soma + CAST(SUBSTRING(p_cpf, i, 1) AS UNSIGNED) * (11 - i);
    SET i = i + 1;
  END WHILE;
  SET resto = soma MOD 11;
  SET dv1 = IF(resto < 2, 0, 11 - resto);
  IF dv1 <> CAST(SUBSTRING(p_cpf, 10, 1) AS UNSIGNED) THEN RETURN 0; END IF;

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

CREATE TRIGGER trg_mov_bi BEFORE INSERT ON movimentacoes_estoque
FOR EACH ROW
BEGIN
  DECLARE v_saldo DECIMAL(14,3);
  DECLARE v_custo DECIMAL(12,4);
  DECLARE v_msg   VARCHAR(128);

  IF NEW.motivo = 'COMPRA' AND (NEW.tipo <> 'ENTRADA' OR NEW.id_pedido IS NOT NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Motivo COMPRA exige tipo ENTRADA e nao pode ter pedido';
  ELSEIF NEW.motivo = 'COMPRA' AND NEW.custo_unitario IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'COMPRA exige o custo_unitario (preco pago por unidade base)';
  ELSEIF NEW.motivo = 'VENDA' AND (NEW.tipo <> 'SAIDA' OR NEW.id_pedido IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Motivo VENDA exige tipo SAIDA e um pedido associado';
  ELSEIF NEW.motivo = 'CANCELAMENTO' AND (NEW.tipo <> 'ENTRADA' OR NEW.id_pedido IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Motivo CANCELAMENTO exige tipo ENTRADA e um pedido associado';
  ELSEIF NEW.motivo = 'PERDA' AND NEW.tipo <> 'SAIDA' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Motivo PERDA exige tipo SAIDA';
  ELSEIF NEW.motivo IN ('PERDA','AJUSTE') AND (NEW.observacao IS NULL OR TRIM(NEW.observacao) = '') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'PERDA e AJUSTE exigem justificativa (observacao)';
  END IF;

  SELECT saldo_atual, custo_atual INTO v_saldo, v_custo
    FROM ingredientes WHERE id_ingrediente = NEW.id_ingrediente FOR UPDATE;

  IF v_saldo IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ingrediente inexistente';
  END IF;

  IF NEW.tipo = 'SAIDA' THEN
    IF v_saldo < NEW.quantidade THEN
      SET v_msg = CONCAT('Estoque insuficiente (ingrediente ', NEW.id_ingrediente,
                         '): saldo ', v_saldo, ', solicitado ', NEW.quantidade);
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;
    SET NEW.custo_unitario = v_custo;
    UPDATE ingredientes
       SET saldo_atual = saldo_atual - NEW.quantidade
     WHERE id_ingrediente = NEW.id_ingrediente;

  ELSEIF NEW.motivo = 'COMPRA' THEN
    UPDATE ingredientes
       SET custo_atual = ROUND((v_saldo * v_custo + NEW.quantidade * NEW.custo_unitario)
                               / (v_saldo + NEW.quantidade), 4),
           saldo_atual = saldo_atual + NEW.quantidade
     WHERE id_ingrediente = NEW.id_ingrediente;

  ELSE
    SET NEW.custo_unitario = COALESCE(NEW.custo_unitario, v_custo);
    UPDATE ingredientes
       SET saldo_atual = saldo_atual + NEW.quantidade
     WHERE id_ingrediente = NEW.id_ingrediente;
  END IF;
END$$

CREATE TRIGGER trg_mov_bu BEFORE UPDATE ON movimentacoes_estoque
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Movimentacoes nao podem ser alteradas: lance um AJUSTE';
END$$

CREATE TRIGGER trg_mov_bd BEFORE DELETE ON movimentacoes_estoque
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Movimentacoes nao podem ser excluidas: lance um AJUSTE';
END$$

CREATE TRIGGER trg_itens_bi BEFORE INSERT ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_status VARCHAR(20);
  DECLARE v_preco  DECIMAL(10,2);
  DECLARE v_ativo  TINYINT(1);

  SELECT status INTO v_status FROM pedidos WHERE id_pedido = NEW.id_pedido FOR UPDATE;
  IF COALESCE(v_status, '') <> 'ABERTO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido inexistente ou nao esta ABERTO: itens nao podem ser alterados';
  END IF;

  SELECT preco, ativo INTO v_preco, v_ativo FROM produtos WHERE id_produto = NEW.id_produto;
  IF v_ativo = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto inativo nao pode ser vendido';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM produto_ingredientes WHERE id_produto = NEW.id_produto) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto sem receita nao pode ser vendido';
  END IF;

  SET NEW.preco_unitario = v_preco;
  SET NEW.preco_opcoes   = 0;
END$$

CREATE TRIGGER trg_itens_bu BEFORE UPDATE ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_status VARCHAR(20);
  DECLARE v_opcoes DECIMAL(10,2);

  IF NEW.id_pedido <> OLD.id_pedido OR NEW.id_produto <> OLD.id_produto
     OR NEW.preco_unitario <> OLD.preco_unitario THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido, produto e preco do item nao podem mudar: exclua e insira novamente';
  END IF;

  SELECT status INTO v_status FROM pedidos WHERE id_pedido = OLD.id_pedido FOR UPDATE;
  IF v_status <> 'ABERTO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido nao esta ABERTO: itens nao podem ser alterados';
  END IF;

  SELECT COALESCE(SUM(preco_adicional), 0) INTO v_opcoes
    FROM itens_pedido_opcoes WHERE id_item = OLD.id_item FOR SHARE;
  SET NEW.preco_opcoes = v_opcoes;
END$$

CREATE TRIGGER trg_itens_bd BEFORE DELETE ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_status VARCHAR(20);

  SELECT status INTO v_status FROM pedidos WHERE id_pedido = OLD.id_pedido FOR UPDATE;
  IF v_status <> 'ABERTO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido nao esta ABERTO: itens nao podem ser removidos';
  END IF;
END$$

CREATE TRIGGER trg_itens_ai AFTER INSERT ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_total DECIMAL(10,2);
  SELECT COALESCE(SUM(subtotal), 0) INTO v_total
    FROM itens_pedido WHERE id_pedido = NEW.id_pedido FOR SHARE;
  UPDATE pedidos SET valor_total = v_total WHERE id_pedido = NEW.id_pedido;
END$$

CREATE TRIGGER trg_itens_au AFTER UPDATE ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_total DECIMAL(10,2);
  SELECT COALESCE(SUM(subtotal), 0) INTO v_total
    FROM itens_pedido WHERE id_pedido = NEW.id_pedido FOR SHARE;
  UPDATE pedidos SET valor_total = v_total WHERE id_pedido = NEW.id_pedido;
END$$

CREATE TRIGGER trg_itens_ad AFTER DELETE ON itens_pedido
FOR EACH ROW
BEGIN
  DECLARE v_total DECIMAL(10,2);
  SELECT COALESCE(SUM(subtotal), 0) INTO v_total
    FROM itens_pedido WHERE id_pedido = OLD.id_pedido FOR SHARE;
  UPDATE pedidos SET valor_total = v_total WHERE id_pedido = OLD.id_pedido;
END$$

CREATE TRIGGER trg_iop_bi BEFORE INSERT ON itens_pedido_opcoes
FOR EACH ROW
BEGIN
  DECLARE v_pedido  INT UNSIGNED;
  DECLARE v_produto INT UNSIGNED;
  DECLARE v_status  VARCHAR(20);
  DECLARE v_prod_op INT UNSIGNED;
  DECLARE v_preco   DECIMAL(10,2);
  DECLARE v_ativo   TINYINT(1);

  SELECT id_pedido, id_produto INTO v_pedido, v_produto
    FROM itens_pedido WHERE id_item = NEW.id_item;
  SELECT status INTO v_status FROM pedidos WHERE id_pedido = v_pedido FOR UPDATE;
  IF COALESCE(v_status, '') <> 'ABERTO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Item inexistente ou pedido nao esta ABERTO: opcoes nao podem ser alteradas';
  END IF;

  SELECT id_produto, preco_adicional, ativo INTO v_prod_op, v_preco, v_ativo
    FROM produto_opcoes WHERE id_opcao = NEW.id_opcao;
  IF v_prod_op IS NULL OR v_prod_op <> v_produto THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A opcao nao pertence ao produto deste item';
  END IF;
  IF v_ativo = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Opcao inativa';
  END IF;

  SET NEW.preco_adicional = v_preco;
END$$

CREATE TRIGGER trg_iop_bu BEFORE UPDATE ON itens_pedido_opcoes
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Opcoes do item nao podem ser alteradas: exclua e insira novamente';
END$$

CREATE TRIGGER trg_iop_bd BEFORE DELETE ON itens_pedido_opcoes
FOR EACH ROW
BEGIN
  DECLARE v_pedido INT UNSIGNED;
  DECLARE v_status VARCHAR(20);

  SELECT id_pedido INTO v_pedido FROM itens_pedido WHERE id_item = OLD.id_item;
  SELECT status INTO v_status FROM pedidos WHERE id_pedido = v_pedido FOR UPDATE;
  IF COALESCE(v_status, '') <> 'ABERTO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido nao esta ABERTO: opcoes nao podem ser removidas';
  END IF;
END$$

CREATE TRIGGER trg_iop_ai AFTER INSERT ON itens_pedido_opcoes
FOR EACH ROW
BEGIN
  DECLARE v_opcoes DECIMAL(10,2);
  SELECT COALESCE(SUM(preco_adicional), 0) INTO v_opcoes
    FROM itens_pedido_opcoes WHERE id_item = NEW.id_item FOR SHARE;
  UPDATE itens_pedido SET preco_opcoes = v_opcoes WHERE id_item = NEW.id_item;
END$$

CREATE TRIGGER trg_iop_ad AFTER DELETE ON itens_pedido_opcoes
FOR EACH ROW
BEGIN
  DECLARE v_opcoes DECIMAL(10,2);
  SELECT COALESCE(SUM(preco_adicional), 0) INTO v_opcoes
    FROM itens_pedido_opcoes WHERE id_item = OLD.id_item FOR SHARE;
  UPDATE itens_pedido SET preco_opcoes = v_opcoes WHERE id_item = OLD.id_item;
END$$

CREATE TRIGGER trg_pedidos_bi BEFORE INSERT ON pedidos
FOR EACH ROW
BEGIN
  SET NEW.status      = 'ABERTO';
  SET NEW.valor_total = 0;
END$$

CREATE TRIGGER trg_pedidos_bu BEFORE UPDATE ON pedidos
FOR EACH ROW
BEGIN
  DECLARE v_soma  DECIMAL(10,2);
  DECLARE v_itens INT;

  SELECT COALESCE(SUM(subtotal), 0), COUNT(*) INTO v_soma, v_itens
    FROM itens_pedido WHERE id_pedido = OLD.id_pedido FOR SHARE;

  IF NEW.valor_total <> v_soma THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'valor_total e calculado pelos itens e nao pode ser alterado manualmente';
  END IF;

  IF OLD.status <> 'ABERTO' AND NOT (
         (NEW.id_cliente <=> OLD.id_cliente)
     AND (NEW.id_funcionario = OLD.id_funcionario)
     AND (NEW.data_hora = OLD.data_hora)
     AND (NEW.forma_pagamento <=> OLD.forma_pagamento)
     AND (NEW.observacao <=> OLD.observacao)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido ja confirmado: somente o status pode ser alterado';
  END IF;

  IF NEW.status <> OLD.status THEN
    IF NOT (   (OLD.status = 'ABERTO'     AND NEW.status IN ('EM_PREPARO','CANCELADO'))
            OR (OLD.status = 'EM_PREPARO' AND NEW.status IN ('PRONTO','CANCELADO'))
            OR (OLD.status = 'PRONTO'     AND NEW.status = 'ENTREGUE')) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Mudanca de status invalida para este pedido';
    END IF;

    IF NEW.status = 'EM_PREPARO' THEN
      IF v_itens = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido sem itens nao pode ser confirmado';
      END IF;
      IF NEW.forma_pagamento IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe a forma de pagamento para confirmar o pedido';
      END IF;
    END IF;
  END IF;
END$$

CREATE TRIGGER trg_pedidos_au AFTER UPDATE ON pedidos
FOR EACH ROW
BEGIN
  IF OLD.status = 'ABERTO' AND NEW.status = 'EM_PREPARO' THEN
    INSERT INTO movimentacoes_estoque
           (id_ingrediente, id_funcionario, id_pedido, tipo, motivo, quantidade)
    SELECT x.id_ingrediente, NEW.id_funcionario, NEW.id_pedido, 'SAIDA', 'VENDA', SUM(x.qtd)
      FROM (
            SELECT pi.id_ingrediente, pi.quantidade * i.quantidade AS qtd
              FROM itens_pedido i
              JOIN produto_ingredientes pi ON pi.id_produto = i.id_produto
             WHERE i.id_pedido = NEW.id_pedido
            UNION ALL
            SELECT o.id_ingrediente, o.quantidade * i.quantidade
              FROM itens_pedido i
              JOIN itens_pedido_opcoes io ON io.id_item = i.id_item
              JOIN produto_opcoes o ON o.id_opcao = io.id_opcao AND o.tipo = 'ADICIONAL'
             WHERE i.id_pedido = NEW.id_pedido
            UNION ALL
            SELECT o.id_ingrediente, -(pi.quantidade * i.quantidade)
              FROM itens_pedido i
              JOIN itens_pedido_opcoes io ON io.id_item = i.id_item
              JOIN produto_opcoes o ON o.id_opcao = io.id_opcao AND o.tipo = 'REMOVER'
              JOIN produto_ingredientes pi ON pi.id_produto = i.id_produto
                                          AND pi.id_ingrediente = o.id_ingrediente
             WHERE i.id_pedido = NEW.id_pedido
           ) x
     GROUP BY x.id_ingrediente
    HAVING SUM(x.qtd) > 0
     ORDER BY x.id_ingrediente;

  ELSEIF OLD.status = 'EM_PREPARO' AND NEW.status = 'CANCELADO' THEN
    INSERT INTO movimentacoes_estoque
           (id_ingrediente, id_funcionario, id_pedido, tipo, motivo, quantidade,
            custo_unitario, observacao)
    SELECT m.id_ingrediente, NEW.id_funcionario, NEW.id_pedido, 'ENTRADA', 'CANCELAMENTO',
           SUM(m.quantidade), MAX(m.custo_unitario), 'Devolucao por cancelamento do pedido'
      FROM movimentacoes_estoque m
     WHERE m.id_pedido = NEW.id_pedido
       AND m.motivo = 'VENDA'
     GROUP BY m.id_ingrediente
     ORDER BY m.id_ingrediente;
  END IF;
END$$

CREATE TRIGGER trg_pedidos_bd BEFORE DELETE ON pedidos
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedidos nao podem ser excluidos: use o status CANCELADO';
END$$

CREATE PROCEDURE sp_alterar_status_pedido(
  IN p_id_pedido       INT UNSIGNED,
  IN p_novo_status     VARCHAR(20),
  IN p_forma_pagamento VARCHAR(20),
  IN p_id_funcionario  INT UNSIGNED
)
BEGIN
  DECLARE v_tentativa TINYINT DEFAULT 0;
  DECLARE v_concluido TINYINT DEFAULT 0;
  DECLARE v_deadlock  TINYINT DEFAULT 0;
  DECLARE v_perfil    VARCHAR(20) DEFAULT NULL;

  DECLARE CONTINUE HANDLER FOR 1213 SET v_deadlock = 1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF p_novo_status NOT IN ('EM_PREPARO','PRONTO','ENTREGUE','CANCELADO') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Status de destino invalido';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pedidos WHERE id_pedido = p_id_pedido) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido nao encontrado';
  END IF;

  SELECT perfil INTO v_perfil
    FROM funcionarios
   WHERE id_funcionario = p_id_funcionario AND ativo = 1;
  IF v_perfil IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Funcionario inexistente ou inativo';
  ELSEIF v_perfil = 'COZINHA' AND p_novo_status <> 'PRONTO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A cozinha so pode marcar o pedido como PRONTO';
  ELSEIF v_perfil = 'ESTOQUE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O perfil ESTOQUE nao pode alterar o status de pedidos';
  END IF;

  WHILE v_concluido = 0 DO
    SET v_tentativa = v_tentativa + 1;
    SET v_deadlock = 0;

    START TRANSACTION;
    UPDATE pedidos
       SET status = p_novo_status,
           forma_pagamento = COALESCE(p_forma_pagamento, forma_pagamento)
     WHERE id_pedido = p_id_pedido;

    IF v_deadlock = 0 THEN
      COMMIT;
      SET v_concluido = 1;
    ELSE
      ROLLBACK;
      IF v_tentativa >= 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sistema ocupado: nao foi possivel concluir, tente novamente';
      END IF;
    END IF;
  END WHILE;
END$$

DELIMITER ;

-- =====================================================================
-- DADOS INICIAIS
-- Senha de todos os funcionarios de exemplo: snackflow123 (BCrypt)
-- =====================================================================

INSERT INTO categorias (nome) VALUES
  ('Lanches'), ('Bebidas'), ('Porções'), ('Sobremesas'), ('Combos');

INSERT INTO funcionarios (id_funcionario, nome, cargo, perfil, login, senha_hash, ativo) VALUES
  (1, 'Carlos Silva',    'Gerente Geral',      'ADMIN',   'admin',          '$2a$10$JXUrtVsv.c0Oawb61EqKl.UzYCBlZ46Z0PnteufvwIAW8gbFZddka', 1),
  (2, 'Ana Paula Souza', 'Operadora de Caixa', 'CAIXA',   'ana.caixa',      '$2a$10$JXUrtVsv.c0Oawb61EqKl.UzYCBlZ46Z0PnteufvwIAW8gbFZddka', 1),
  (3, 'Marcos Oliveira', 'Chapeiro',           'COZINHA', 'marcos.cozinha', '$2a$10$JXUrtVsv.c0Oawb61EqKl.UzYCBlZ46Z0PnteufvwIAW8gbFZddka', 1),
  (4, 'Julia Mendes',    'Estoquista',         'ESTOQUE', 'julia.estoque',  '$2a$10$JXUrtVsv.c0Oawb61EqKl.UzYCBlZ46Z0PnteufvwIAW8gbFZddka', 1),
  (5, 'Roberto Alves',   'Auxiliar',           'CAIXA',   'roberto.antigo', '$2a$10$JXUrtVsv.c0Oawb61EqKl.UzYCBlZ46Z0PnteufvwIAW8gbFZddka', 0);

INSERT INTO clientes (id_cliente, nome, cpf, telefone) VALUES
  (1, 'Mariana Costa',   '39053344705', '(48) 98765-4321'),
  (2, 'Lucas Fernandes', '11144477735', '(48) 91234-5678'),
  (3, 'Beatriz Lima',    '52998224725', '(48) 99876-5432'),
  (4, 'Pedro Rocha',     NULL,          '(48) 99111-2233');

INSERT INTO ingredientes (id_ingrediente, nome, unidade, estoque_minimo) VALUES
  (1,  'Pão Brioche',               'UN', 15),
  (2,  'Hambúrguer Artesanal 160g', 'UN', 10),
  (3,  'Queijo Cheddar Fatiado',    'G',  500),
  (4,  'Bacon Fatiado',             'G',  400),
  (5,  'Cebola Roxa',               'G',  300),
  (6,  'Batata Palito Congelada',   'G',  1500),
  (7,  'Refrigerante Lata 350ml',   'UN', 12),
  (8,  'Sorvete Creme',             'ML', 500),
  (9,  'Leite Integral',            'ML', 1000),
  (10, 'Tomate',                    'G',  300);

INSERT INTO produtos (id_produto, nome, descricao, id_categoria, preco, ativo) VALUES
  (1, 'X-Burger Clássico',            'Pão brioche, hambúrguer 160g, cheddar e cebola roxa', 1, 24.00, 1),
  (2, 'X-Bacon Supremo',              'Pão brioche, hambúrguer 160g, cheddar e bacon',       1, 29.50, 1),
  (3, 'X-Salada',                     'Pão brioche, hambúrguer 160g, cheddar, tomate e cebola', 1, 26.00, 1),
  (4, 'Batata Frita Crocante (400g)', 'Porção de batata palito',                             3, 18.00, 1),
  (5, 'Refrigerante Lata 350ml',      NULL,                                                  2, 6.50,  1),
  (6, 'Milkshake Baunilha 400ml',     'Sorvete de creme batido com leite',                   4, 16.00, 1),
  (7, 'Combo Clássico',               'X-Burger Clássico com refrigerante lata',             5, 28.00, 1),
  (8, 'Suco de Laranja 300ml',        'Em breve',                                            2, 9.00,  0);

INSERT INTO produto_ingredientes (id_produto, id_ingrediente, quantidade) VALUES
  (1, 1, 1), (1, 2, 1), (1, 3, 40), (1, 5, 20),
  (2, 1, 1), (2, 2, 1), (2, 3, 40), (2, 4, 50),
  (3, 1, 1), (3, 2, 1), (3, 3, 30), (3, 10, 40), (3, 5, 10),
  (4, 6, 400),
  (5, 7, 1),
  (6, 8, 250), (6, 9, 150),
  (7, 1, 1), (7, 2, 1), (7, 3, 40), (7, 5, 20), (7, 7, 1);

INSERT INTO produto_opcoes (id_opcao, id_produto, tipo, nome, id_ingrediente, quantidade, preco_adicional) VALUES
  (1,  1, 'ADICIONAL', 'Bacon extra',      4,  40,   4.50),
  (2,  1, 'ADICIONAL', 'Hambúrguer extra', 2,  1,    7.00),
  (3,  1, 'REMOVER',   'Cebola',           5,  NULL, 0.00),
  (4,  2, 'ADICIONAL', 'Dobro de bacon',   4,  50,   5.00),
  (5,  2, 'REMOVER',   'Cheddar',          3,  NULL, 0.00),
  (6,  3, 'ADICIONAL', 'Bacon extra',      4,  40,   4.50),
  (7,  3, 'REMOVER',   'Tomate',           10, NULL, 0.00),
  (8,  3, 'REMOVER',   'Cebola',           5,  NULL, 0.00),
  (9,  4, 'ADICIONAL', 'Cheddar extra',    3,  40,   4.00),
  (10, 4, 'ADICIONAL', 'Bacon extra',      4,  30,   4.00);

INSERT INTO movimentacoes_estoque
  (id_ingrediente, id_funcionario, tipo, motivo, quantidade, custo_unitario, data_hora, observacao) VALUES
  (1,  4, 'ENTRADA', 'COMPRA', 50,   1.8000, NOW() - INTERVAL 3 DAY, 'Padaria Central NF-892'),
  (2,  4, 'ENTRADA', 'COMPRA', 50,   4.0000, NOW() - INTERVAL 3 DAY, 'Frigorífico Serra NF-1204'),
  (2,  4, 'ENTRADA', 'COMPRA', 50,   5.0000, NOW() - INTERVAL 3 DAY, 'Frigorífico Serra NF-1219'),
  (3,  4, 'ENTRADA', 'COMPRA', 2000, 0.0450, NOW() - INTERVAL 3 DAY, 'Laticínios Vale NF-377'),
  (4,  4, 'ENTRADA', 'COMPRA', 600,  0.0600, NOW() - INTERVAL 3 DAY, 'Frigorífico Serra NF-1204'),
  (5,  4, 'ENTRADA', 'COMPRA', 1500, 0.0100, NOW() - INTERVAL 3 DAY, 'Hortifruti Sol NF-5521'),
  (6,  4, 'ENTRADA', 'COMPRA', 6000, 0.0180, NOW() - INTERVAL 3 DAY, 'Atacadão NF-48817'),
  (7,  4, 'ENTRADA', 'COMPRA', 24,   2.6000, NOW() - INTERVAL 3 DAY, 'Atacadão NF-48817'),
  (8,  4, 'ENTRADA', 'COMPRA', 3000, 0.0200, NOW() - INTERVAL 3 DAY, 'Laticínios Vale NF-377'),
  (9,  4, 'ENTRADA', 'COMPRA', 5000, 0.0055, NOW() - INTERVAL 3 DAY, 'Laticínios Vale NF-377'),
  (10, 4, 'ENTRADA', 'COMPRA', 1000, 0.0120, NOW() - INTERVAL 3 DAY, 'Hortifruti Sol NF-5521');

INSERT INTO movimentacoes_estoque
  (id_ingrediente, id_funcionario, tipo, motivo, quantidade, data_hora, observacao) VALUES
  (4, 4, 'SAIDA', 'PERDA',  150, NOW() - INTERVAL 3 DAY, 'Descarte por validade vencida na câmara fria'),
  (7, 4, 'SAIDA', 'PERDA',  12,  NOW() - INTERVAL 3 DAY, 'Latas amassadas na entrega'),
  (3, 4, 'SAIDA', 'AJUSTE', 30,  NOW() - INTERVAL 2 DAY, 'Inventário: diferença na contagem');

INSERT INTO pedidos (id_pedido, id_cliente, id_funcionario, data_hora) VALUES
  (1, 1,    2, NOW() - INTERVAL 2 DAY),
  (2, NULL, 2, NOW() - INTERVAL 1 DAY),
  (3, 2,    1, NOW() - INTERVAL 1 DAY),
  (4, NULL, 2, NOW() - INTERVAL 1 DAY),
  (5, 1,    2, NOW() - INTERVAL 30 MINUTE),
  (6, NULL, 2, NOW() - INTERVAL 10 MINUTE);

INSERT INTO itens_pedido (id_item, id_pedido, id_produto, quantidade, observacao) VALUES
  (1,  1, 1, 1, 'Ponto da carne ao ponto'),
  (2,  1, 5, 1, NULL),
  (3,  2, 2, 2, NULL),
  (4,  3, 4, 1, NULL),
  (5,  3, 6, 1, NULL),
  (6,  4, 3, 2, NULL),
  (7,  5, 1, 1, NULL),
  (8,  5, 5, 1, NULL),
  (9,  6, 2, 1, NULL),
  (10, 6, 4, 1, 'Bem sequinha');

INSERT INTO itens_pedido_opcoes (id_item, id_opcao) VALUES
  (1, 1), (1, 3),
  (4, 9),
  (6, 7),
  (7, 2);

CALL sp_alterar_status_pedido(1, 'EM_PREPARO', 'PIX',      2);
CALL sp_alterar_status_pedido(1, 'PRONTO',     NULL,       3);
CALL sp_alterar_status_pedido(1, 'ENTREGUE',   NULL,       2);

CALL sp_alterar_status_pedido(2, 'EM_PREPARO', 'DINHEIRO', 2);
CALL sp_alterar_status_pedido(2, 'PRONTO',     NULL,       3);
CALL sp_alterar_status_pedido(2, 'ENTREGUE',   NULL,       2);

CALL sp_alterar_status_pedido(3, 'EM_PREPARO', 'DEBITO',   1);
CALL sp_alterar_status_pedido(3, 'PRONTO',     NULL,       3);
CALL sp_alterar_status_pedido(3, 'ENTREGUE',   NULL,       1);

CALL sp_alterar_status_pedido(4, 'EM_PREPARO', 'CREDITO',  2);
CALL sp_alterar_status_pedido(4, 'CANCELADO',  NULL,       2);

CALL sp_alterar_status_pedido(5, 'EM_PREPARO', 'PIX',      2);
CALL sp_alterar_status_pedido(5, 'PRONTO',     NULL,       3);

CALL sp_alterar_status_pedido(6, 'EM_PREPARO', 'CREDITO',  2);
