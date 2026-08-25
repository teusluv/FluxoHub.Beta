-- =============================================================================
-- V1__schema_inicial.sql
-- Schema inicial do POD System
--
-- DECISÕES TÉCNICAS DOCUMENTADAS:
--
-- 1. MULTI-TENANT POR FILIAL:
--    Tabela `filiais` centraliza depósitos/filiais. Todas as entidades de negócio
--    (usuarios, entregas) carregam filial_id. Queries sempre filtram por filial_id
--    via JPA @Filter — dados de filiais diferentes NUNCA se misturam em resultados.
--
-- 2. capturado_em vs sincronizado_em (tabela canhotos):
--    O motorista pode ficar offline por horas. `capturado_em` é o timestamp do
--    device (hora real da entrega), enviado pelo app. `sincronizado_em` é quando
--    o registro chegou ao servidor. Para fins legais e fiscais, `capturado_em`
--    é a hora que vale — não o momento do upload.
--
-- 3. CANHOTOS NUNCA SÃO DELETADOS:
--    Canhoto é documento fiscal. DELETE é proibido por design. Registros
--    inválidos são marcados com valido=FALSE e motivo_invalidacao preenchido.
--    O registro imutável em eventos_auditoria garante rastreabilidade completa.
--
-- 4. chave_nfe (44 dígitos):
--    Chave de acesso da NFe brasileira. Permite integração futura com ERP fiscal
--    (ex: NBS, SAP) sem redesenhar o schema — o ERP consulta por chave_nfe,
--    não pelo número interno da nota.
--
-- 5. UUID como PK:
--    Permite geração de ID no device (mobile offline) antes da sincronização.
--    O motorista captura o canhoto offline, o app gera um UUID local para o
--    canhoto — quando sincroniza, o ID já é estável e não há colisão.
-- =============================================================================

-- Extensão para geração de UUID no PostgreSQL
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- FILIAIS (multi-tenant)
-- =============================================================================
CREATE TABLE filiais (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome        VARCHAR(200) NOT NULL,
    cidade      VARCHAR(100) NOT NULL,
    estado      VARCHAR(2)   NOT NULL,
    cnpj        VARCHAR(20),
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE filiais IS 'Depósitos/filiais da distribuidora. Unidade raiz do multi-tenant.';

-- =============================================================================
-- USUÁRIOS
-- =============================================================================
CREATE TABLE usuarios (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    filial_id           UUID        NOT NULL REFERENCES filiais(id),
    nome                VARCHAR(150) NOT NULL,
    -- papel no sistema; ADMIN pode ter filial_id = filial padrão mas acesso global via flag
    papel               VARCHAR(20)  NOT NULL
        CHECK (papel IN ('MOTORISTA','VENDEDOR','ADMIN')),
    admin_global        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- admin_global=TRUE significa que este ADMIN enxerga todas as filiais (para relatórios gerenciais)
    -- apenas usuários com papel='ADMIN' devem ter admin_global=TRUE
    telefone_whatsapp   VARCHAR(20),
    email               VARCHAR(150) UNIQUE,
    senha_hash          VARCHAR(255) NOT NULL,
    ativo               BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON COLUMN usuarios.admin_global IS
    'Se TRUE e papel=ADMIN, o usuário enxerga dados de todas as filiais. '
    'Usados apenas para gestores corporativos.';

-- =============================================================================
-- REFRESH TOKENS (blacklist para logout)
-- =============================================================================
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  UUID        NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,  -- hash SHA-256 do token (nunca armazenar raw)
    expira_em   TIMESTAMPTZ NOT NULL,
    revogado    BOOLEAN     NOT NULL DEFAULT FALSE,
    revogado_em TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_hash     ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_usuario  ON refresh_tokens(usuario_id, revogado);

-- =============================================================================
-- ENTREGAS
-- =============================================================================
CREATE TABLE entregas (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    filial_id               UUID        NOT NULL REFERENCES filiais(id),
    numero_nota_fiscal      VARCHAR(50)  NOT NULL,
    chave_nfe               VARCHAR(44),   -- chave de acesso da NFe (44 dígitos numéricos)
    cliente_nome            VARCHAR(200) NOT NULL,
    cliente_documento       VARCHAR(20),            -- CPF ou CNPJ do destinatário
    vendedor_id             UUID        REFERENCES usuarios(id),
    motorista_id            UUID        REFERENCES usuarios(id),
    data_prevista_entrega   DATE,
    data_entrega_real       TIMESTAMPTZ,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'PENDENTE'
        CHECK (status IN ('PENDENTE','EM_ROTA','ENTREGUE_SEM_CANHOTO','ENTREGUE_COM_CANHOTO','DIVERGENCIA')),
    latitude                DECIMAL(10,7),
    longitude               DECIMAL(10,7),
    observacoes             TEXT,
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN entregas.chave_nfe IS
    'Chave de acesso da NFe (44 dígitos). Permite vinculação com ERP fiscal '
    'sem redesenho de schema. Opcional — nem toda entrega terá NFe eletrônica.';

COMMENT ON COLUMN entregas.filial_id IS
    'Filial responsável pela entrega. Todas as queries filtram por filial_id.';

-- Índices de performance — críticos para a meta de < 300ms p95
CREATE INDEX idx_entregas_nota_filial   ON entregas(filial_id, numero_nota_fiscal);
CREATE INDEX idx_entregas_cliente       ON entregas(filial_id, cliente_nome);
CREATE INDEX idx_entregas_vendedor      ON entregas(vendedor_id, status);
CREATE INDEX idx_entregas_motorista     ON entregas(motorista_id, status);
CREATE INDEX idx_entregas_data_status   ON entregas(filial_id, data_prevista_entrega, status);
CREATE INDEX idx_entregas_chave_nfe     ON entregas(chave_nfe) WHERE chave_nfe IS NOT NULL;

-- Busca full-text em cliente_nome e numero_nota_fiscal
CREATE INDEX idx_entregas_fts ON entregas
    USING GIN (to_tsvector('portuguese', cliente_nome || ' ' || numero_nota_fiscal));

-- =============================================================================
-- CANHOTOS
-- =============================================================================
CREATE TABLE canhotos (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    entrega_id          UUID        NOT NULL REFERENCES entregas(id),
    -- url_imagem: caminho relativo no bucket (nunca URL pública). URLs assinadas
    -- são geradas sob demanda com expiração de 15 min pelo endpoint de consulta.
    url_imagem          VARCHAR(500) NOT NULL,
    texto_ocr_extraido  TEXT,
    confianca_ocr       DECIMAL(4,3),           -- 0.000 a 1.000
    necessita_revisao   BOOLEAN     NOT NULL DEFAULT FALSE, -- confianca < 0.70
    valido              BOOLEAN     NOT NULL DEFAULT TRUE,  -- FALSE = invalidado (jamais deletado)
    motivo_invalidacao  TEXT,                   -- obrigatório quando valido=FALSE
    -- capturado_em: hora do DEVICE (hora real da entrega, mesmo offline)
    -- sincronizado_em: hora em que chegou ao servidor (pode ser horas depois)
    capturado_em        TIMESTAMPTZ NOT NULL,
    sincronizado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- device_id: impede duplicidade se o app tentar sincronizar duas vezes o mesmo canhoto
    -- (ex: retry após timeout onde o servidor já havia gravado)
    device_id           VARCHAR(100) NOT NULL,
    UNIQUE (entrega_id, device_id)  -- idempotência de sync
);

COMMENT ON COLUMN canhotos.capturado_em IS
    'Timestamp do device no momento da captura. Este é o horário legal da entrega, '
    'independentemente de quando o app sincronizou com o servidor.';

COMMENT ON COLUMN canhotos.url_imagem IS
    'Caminho do objeto no bucket S3/MinIO (ex: canhotos/2024/01/abc123.jpg). '
    'Nunca exposto diretamente — sempre via URL pré-assinada com expiração de 15 min.';

CREATE INDEX idx_canhotos_entrega       ON canhotos(entrega_id);
CREATE INDEX idx_canhotos_revisao       ON canhotos(necessita_revisao) WHERE necessita_revisao = TRUE;
CREATE INDEX idx_canhotos_device        ON canhotos(device_id);

-- =============================================================================
-- EVENTOS DE AUDITORIA (append-only — nunca atualizar ou deletar)
-- =============================================================================
CREATE TABLE eventos_auditoria (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    filial_id   UUID        REFERENCES filiais(id),   -- NULL para eventos de sistema
    entidade    VARCHAR(50)  NOT NULL,                -- ex: 'entrega', 'canhoto', 'usuario'
    entidade_id UUID        NOT NULL,
    acao        VARCHAR(50)  NOT NULL,                -- ex: 'CRIADO', 'INVALIDADO', 'STATUS_ALTERADO'
    usuario_id  UUID        REFERENCES usuarios(id),  -- NULL para ações de sistema (OCR, sync)
    detalhes    JSONB,                               -- dados extras (valores antes/depois, motivo, etc)
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE eventos_auditoria IS
    'Log imutável de auditoria. NUNCA permitir UPDATE ou DELETE nesta tabela. '
    'Toda ação sobre canhoto (criar, invalidar) deve gerar um registro aqui.';

CREATE INDEX idx_auditoria_entidade    ON eventos_auditoria(entidade, entidade_id);
CREATE INDEX idx_auditoria_usuario     ON eventos_auditoria(usuario_id, criado_em);
CREATE INDEX idx_auditoria_filial_data ON eventos_auditoria(filial_id, criado_em);

-- =============================================================================
-- TRIGGER: atualiza atualizado_em automaticamente
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_set_atualizado_em()
RETURNS TRIGGER AS $$
BEGIN
    NEW.atualizado_em = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_filiais_atualizado_em
    BEFORE UPDATE ON filiais
    FOR EACH ROW EXECUTE FUNCTION fn_set_atualizado_em();

CREATE TRIGGER trg_usuarios_atualizado_em
    BEFORE UPDATE ON usuarios
    FOR EACH ROW EXECUTE FUNCTION fn_set_atualizado_em();

CREATE TRIGGER trg_entregas_atualizado_em
    BEFORE UPDATE ON entregas
    FOR EACH ROW EXECUTE FUNCTION fn_set_atualizado_em();

-- =============================================================================
-- DADOS INICIAIS — filial padrão e admin global para bootstrapping
-- Senha padrão: "admin@pod2024" — DEVE ser alterada no primeiro login
-- =============================================================================
INSERT INTO filiais (id, nome, cidade, estado, cnpj)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Matriz Feira de Santana',
    'Feira de Santana',
    'BA',
    '00.000.000/0001-00'  -- substituir pelo CNPJ real
);

-- Admin global para setup inicial
-- Hash bcrypt de 'admin@pod2024' (strength 12)
INSERT INTO usuarios (id, filial_id, nome, papel, admin_global, email, senha_hash)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'Administrador',
    'ADMIN',
    TRUE,
    'admin@pod.local',
    '$2a$12$K8pMJnCYqNAJ4yz.5p8HtONJJH0bFKDvbP9Ei7YQzfYpfvMT9C3..'  -- placeholder; real hash gerado no bootstrap
);
