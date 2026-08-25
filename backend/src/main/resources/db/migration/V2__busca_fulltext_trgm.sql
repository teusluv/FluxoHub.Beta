-- =============================================================================
-- Migração Fase 4: Otimização de Busca Full-Text (< 300ms)
-- =============================================================================

-- Habilita a extensão pg_trgm (trigramas) para acelerar consultas LIKE '%termo%'
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- O índice V1 usava to_tsvector, que exige match exato de palavras (lexemas).
-- O usuário muitas vezes busca por partes da nota (ex: "123" para "00012345").
-- Trigramas (pg_trgm) resolvem isso acelerando o ILIKE/LIKE diretamente,
-- sem precisar alterar a Specification do JPA no backend.

-- Remove o índice antigo de tsvector que não estava sendo usado pelo JPA LIKE
DROP INDEX IF EXISTS idx_entregas_fts;

-- Cria os índices GIN otimizados para busca parcial de texto, 
-- aplicando lower() para casar com a constraint cb.lower() do JPA Criteria
CREATE INDEX idx_entregas_nota_trgm ON entregas USING GIN (lower(numero_nota_fiscal) gin_trgm_ops);
CREATE INDEX idx_entregas_cliente_trgm ON entregas USING GIN (lower(cliente_nome) gin_trgm_ops);
CREATE INDEX idx_usuarios_nome_trgm ON usuarios USING GIN (lower(nome) gin_trgm_ops);

-- Cria um índice GIN para busca na chave de acesso (se precisarem buscar pelo meio da chave)
CREATE INDEX idx_entregas_chave_nfe_trgm ON entregas USING GIN (chave_nfe gin_trgm_ops);

-- Índices B-Tree normais para FKs e datas já foram criados na V1 (idx_entregas_vendedor, idx_entregas_motorista, etc)
-- Mas vamos garantir que a busca combinada seja rápida
CREATE INDEX IF NOT EXISTS idx_entregas_data_criacao ON entregas(criado_em DESC);
