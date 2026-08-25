# ADR-003: Rastro de Auditoria Imutável

**Status:** Aceito  
**Data:** 2024-01

## Contexto

Canhotos de entrega têm valor legal e fiscal. Em caso de disputa com cliente ou
questionamento pela Receita Federal, é necessário provar:
- Quem registrou o canhoto
- Quando foi registrado (hora real vs. hora de sync)
- Se foi invalidado, por quem e por quê

## Decisões

### 1. Canhotos nunca são deletados

`DELETE` na tabela `canhotos` é proibido por design. Registros inválidos recebem
`valido = FALSE` com `motivo_invalidacao` obrigatório. Isso garante que:
- O histórico completo sempre existe
- Não há como "apagar" um canhoto para cobrir uma entrega problemática
- Auditorias fiscais sempre têm acesso ao registro original

**Implementação:** o `CanhotoService` nunca chama `repository.delete()`. A invalidação
é uma operação de update com auditoria obrigatória.

### 2. Tabela `eventos_auditoria` append-only

Toda ação sobre entidades críticas gera um registro em `eventos_auditoria`. Esta tabela:
- **Nunca recebe UPDATE ou DELETE** — regra enforçada no nível do service (não há método no repository)
- Contém `detalhes JSONB` com snapshot dos dados antes/depois da mudança
- Inclui `usuario_id` (ou NULL para ações de sistema como OCR automático)

### 3. `capturado_em` vs `sincronizado_em`

- `capturado_em`: timestamp do device no momento da foto (hora legal da entrega)
- `sincronizado_em`: quando chegou ao servidor (pode ser horas depois)

Para fins de prova de entrega, `capturado_em` é o que vale — o motorista pode estar
em área sem sinal por horas. Isso é explícito na documentação e no comentário do schema.

## Consequências

**Positivas:**
- Rastreabilidade completa para disputas e auditorias
- Impossibilidade de adulteração de registros sem deixar rastro

**Negativas:**
- Crescimento da tabela `eventos_auditoria` ao longo do tempo
- Mitigação: particionar por data em versões futuras se volume exigir
- Política de retenção: manter todos os registros por pelo menos 5 anos (obrigação fiscal)
