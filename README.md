# FluxoHub — Gestão Digital de Canhotos de Entrega

Sistema **Proof of Delivery (PoD) digital** para substituir o processo manual de canhotos de papel + WhatsApp em distribuidoras. Suporta múltiplos depósitos/filiais.

## Problema resolvido

Vendedor encontra qualquer canhoto em **< 10 segundos** pelo número da nota fiscal, sem depender de ninguém.

## Monorepo

| Pasta | Tecnologia | Papel |
|---|---|---|
| `/backend` | Java 21 + Spring Boot 3.x | API REST, OCR, auth, regras de negócio |
| `/mobile` | React Native + Expo | App do motorista (offline-first) |
| `/web-admin` | Next.js 14 + shadcn/ui | Dashboard admin + busca de vendedores |
| `/docs` | Markdown + ADRs | Arquitetura e decisões técnicas |

## Quick Start (Desenvolvimento Local)

### Pré-requisitos
- Docker + Docker Compose v2
- Java 21 (JDK)
- Node.js 20+
- (Opcional) Expo CLI para mobile

### 1. Configurar variáveis de ambiente

```bash
cp .env.example .env
# Edite .env com seus valores
```

### 2. Subir infra local

```bash
docker-compose up -d postgres minio adminer
```

### 3. Rodar o backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. Rodar o admin web

```bash
cd web-admin
npm install
npm run dev
```

### 5. Rodar o app mobile

```bash
cd mobile
npm install
npx expo start
```

## Serviços disponíveis em dev

| Serviço | URL |
|---|---|
| Backend API | http://localhost:8080 |
| API Docs (Swagger) | http://localhost:8080/swagger-ui.html |
| Admin Web | http://localhost:3000 |
| MinIO Console | http://localhost:9001 |
| Adminer (DB) | http://localhost:8888 |

## Fases de implementação

- [x] **Fase 0** — Fundação: monorepo, Docker Compose, CI
- [x] **Fase 1** — Auth e RBAC (JWT dual-token, 3 papéis, escopo de filial)
- [x] **Fase 2** — Domínio de Entregas (CRUD, paginação, filtros)
- [ ] **Fase 3** — Captura de Canhotos + OCR (upload, batch sync)
- [ ] **Fase 4** — Busca full-text (< 300ms p95)
- [ ] **Fase 5** — App Mobile do Motorista (offline-first)
- [ ] **Fase 6** — Bot WhatsApp (webhook, consulta por NF)
- [ ] **Fase 7** — Dashboard Admin (KPIs, alertas)

## Documentação

- [Arquitetura geral](docs/architecture.md)
- [ADR-001: Estratégia Offline-First](docs/adr/ADR-001-offline-first.md)
- [ADR-002: Estratégia OCR](docs/adr/ADR-002-ocr-strategy.md)
- [ADR-003: Rastro de Auditoria](docs/adr/ADR-003-audit-trail.md)
- [ADR-004: Multi-tenant por Filial](docs/adr/ADR-004-multi-tenant.md)
