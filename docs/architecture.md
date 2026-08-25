# Arquitetura do POD System

## Visão Geral

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           POD System — Arquitetura                           │
└──────────────────────────────────────────────────────────────────────────────┘

  [App Mobile Motorista]
    React Native + Expo
    SQLite local (offline-first)          ──────────────────────────────┐
    Fila de sync com retry exponencial                                   │
                                                                         ▼
  [Admin Web Next.js]                                       ┌─────────────────────┐
    App Router, shadcn/ui                 ────────────────► │   Spring Boot API   │
    Busca com debounce 300ms                                │   Java 21, Port 8080│
                                                            └──────┬──────┬───────┘
  [WhatsApp Bot]                                                   │      │
    Webhook Meta Cloud API                ────────────────────────►│      │
    Rate limiting por telefone                                      │      │
                                                                    │      │
                                              ┌─────────────────────┘      │
                                              │                             │
                                              ▼                             ▼
                                    ┌──────────────────┐       ┌───────────────────┐
                                    │   PostgreSQL 15  │       │   MinIO / S3      │
                                    │   Port 5432      │       │   Port 9000       │
                                    │   Flyway schema  │       │   Bucket: canhotos│
                                    └──────────────────┘       └───────────────────┘
                                                                          │
                                                                          ▼
                                                              ┌───────────────────────┐
                                                              │ Google Cloud Vision   │
                                                              │ (OCR assíncrono)      │
                                                              └───────────────────────┘
```

## Fluxo crítico: Captura de Canhoto Offline

```
1. Motorista tira foto
        │
        ▼
2. App salva no SQLite local
   status: PENDENTE_SYNC
   (UUID gerado no device)
        │
        ▼ (background worker)
3. Tem rede?
   ├── NÃO → retry exponencial (5s → 15s → 60s → 5min)
   └── SIM →
        │
        ▼
4. POST /api/v1/canhotos/batch-sync
   (multipart com imagem + metadados)
        │
        ▼
5. Backend:
   a. Salva imagem no MinIO
   b. Cria registro em canhotos (status: sincronizado_em = now())
   c. Atualiza entrega.status = ENTREGUE_SEM_CANHOTO
   d. Dispara OCR assíncrono (@Async)
   e. Retorna 201 ao mobile
        │
        ▼ (async)
6. OCR via Cloud Vision
   ├── confiança ≥ 70% → match NF → status = ENTREGUE_COM_CANHOTO
   └── confiança < 70% → necessita_revisao = true (revisão manual)
        │
        ▼
7. Mobile atualiza SQLite local
   status: SINCRONIZADO (indicador verde para o motorista)
```

## Modelo de Domínio

```
filiais (1) ──── (N) usuarios
                         │
filiais (1) ──── (N) entregas ──── (N) canhotos
                         │
                         └──────── eventos_auditoria
```

## Decisões técnicas principais

Ver [ADRs](./adr/) para documentação completa de cada decisão.

| Decisão | Arquivo |
|---|---|
| Offline-first no mobile | [ADR-001](./adr/ADR-001-offline-first.md) |
| OCR: Cloud Vision vs Tesseract | [ADR-002](./adr/ADR-002-ocr-strategy.md) |
| Auditoria imutável | [ADR-003](./adr/ADR-003-audit-trail.md) |
| Multi-tenant por filial | [ADR-004](./adr/ADR-004-multi-tenant.md) |
