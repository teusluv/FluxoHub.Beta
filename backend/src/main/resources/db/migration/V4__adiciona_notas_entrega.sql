-- V4__adiciona_notas_entrega.sql
-- Tabela para registro de notas e observações complementares vinculadas a entregas

CREATE TABLE notas_entrega (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrega_id      UUID NOT NULL REFERENCES entregas(id) ON DELETE CASCADE,
    filial_id       UUID NOT NULL REFERENCES filiais(id),
    autor_id        UUID NOT NULL REFERENCES usuarios(id),
    autor_nome      VARCHAR(200) NOT NULL,
    autor_papel     VARCHAR(30) NOT NULL,
    tipo            VARCHAR(30) NOT NULL DEFAULT 'GERAL',
    conteudo        TEXT NOT NULL,
    idempotency_key VARCHAR(64),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índices para busca rápida por entrega (ordenação decrescente de data) e por filial
CREATE INDEX idx_notas_entrega_entrega ON notas_entrega(entrega_id, criado_em DESC);
CREATE INDEX idx_notas_entrega_filial  ON notas_entrega(filial_id);

-- Restrição de idempotência por entrega
CREATE UNIQUE INDEX uq_notas_entrega_idempotency 
    ON notas_entrega(entrega_id, idempotency_key) 
    WHERE idempotency_key IS NOT NULL;
