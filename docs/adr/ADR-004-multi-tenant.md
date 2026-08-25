# ADR-004: Multi-tenant por Filial

**Status:** Aceito  
**Data:** 2024-01

## Contexto

A distribuidora já tem 1 filial em Feira de Santana, mas planeja expansão para
outras cidades. O schema precisa suportar múltiplas filiais sem redesenho.

## Decisão

**Abordagem:** Shared database, shared schema com `filial_id` em todas as tabelas de negócio.

### Alternativas consideradas

| Abordagem | Prós | Contras | Decisão |
|---|---|---|---|
| Banco separado por filial | Isolamento total | Complexidade de deploy, backup, migrations | ❌ |
| Schema separado por filial | Bom isolamento | Migrations complexas, N schemas para gerenciar | ❌ |
| `filial_id` em todas as tabelas | Simples, um banco, uma migration | Risco de vazar dados se query errada | ✅ |

### Garantias de isolamento implementadas

1. **JPA `@Filter`:** toda `@Entity` com `filial_id` tem um filtro Hibernate habilitado
   automaticamente via `TenantContext` (thread-local). Queries sem filtro de filial
   não compilam em tempo de execução.

2. **`FilialContextFilter` (Servlet Filter):** extrai o `filial_id` do JWT do usuário
   autenticado e popula o `TenantContext` antes de qualquer controller ser invocado.

3. **ADMIN Global:** usuários com `admin_global = TRUE` e `papel = ADMIN` podem
   desabilitar o filtro de filial para relatórios consolidados. Isso é uma permissão
   explícita, não o comportamento padrão.

4. **Índices compostos:** todos os índices de performance incluem `filial_id` como
   primeira coluna (`idx_entregas_nota_filial`, etc.) para que o plano de query
   do Postgres sempre filtre por filial antes de qualquer outra condição.

## Schema impactado

- `filiais`: tabela raiz do tenant
- `usuarios`: `filial_id NOT NULL` (filial de origem do usuário)
- `entregas`: `filial_id NOT NULL`
- `eventos_auditoria`: `filial_id` (nullable — eventos de sistema não têm filial)
- `canhotos`: isolamento via `entrega_id → entregas.filial_id` (join implícito)
- `refresh_tokens`: isolamento via `usuario_id → usuarios.filial_id`

## Consequências

**Positivas:**
- Uma única instância do backend serve N filiais
- Relatórios consolidados possíveis para ADMIN global
- Migrations simples (um único schema)

**Negativas / Riscos:**
- Bug em `FilialContextFilter` pode vazar dados entre filiais
- Mitigação: testes de integração específicos que verificam isolamento de filial
- Dívida técnica: se escala para 50+ filiais com volumes muito diferentes,
  pode ser necessário migrar para schema separado por filial
