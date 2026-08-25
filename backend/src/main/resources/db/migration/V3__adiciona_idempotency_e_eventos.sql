-- V3__adiciona_idempotency_e_eventos.sql

-- Adiciona idempotency_key à tabela de entregas para garantir unicidade e processamento único
ALTER TABLE entregas ADD COLUMN idempotency_key VARCHAR(64);

-- Cria tabela de eventos de entrega para fins de auditoria/timeline (audit trail)
CREATE TABLE entrega_eventos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrega_id      UUID NOT NULL REFERENCES entregas(id) ON DELETE CASCADE,
    status_anterior VARCHAR(30),
    status_novo     VARCHAR(30) NOT NULL,
    ator_id         UUID NOT NULL REFERENCES usuarios(id),
    origem          VARCHAR(20) NOT NULL, -- 'WEB_ADMIN' | 'APP_MOTORISTA' | 'SYSTEM'
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_entrega_eventos_entrega ON entrega_eventos(entrega_id);
CREATE INDEX idx_entrega_eventos_criado  ON entrega_eventos(criado_em);
