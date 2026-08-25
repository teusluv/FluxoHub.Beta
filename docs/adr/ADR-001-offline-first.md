# ADR-001: Estratégia Offline-First no App Mobile

**Status:** Aceito  
**Data:** 2024-01  
**Autores:** Time POD

## Contexto

Motoristas operam em áreas com sinal instável (galpões, zonas industriais, interior da Bahia).
Bloquear a captura do canhoto esperando resposta da API significaria:
- Motorista parado esperando o app responder
- Risco de perder a janela com o cliente (que vai embora ou fica impaciente)
- Frustração que leva ao abandono do app e retorno ao papel/WhatsApp

## Decisão

**Local-first absoluto:** toda captura de canhoto é gravada primeiro no SQLite local do device,
com status `PENDENTE_SYNC`. O app nunca bloqueia o motorista esperando rede.

Um worker em background (`SyncQueue`) tenta sincronizar com retry exponencial:
- Primeira tentativa: imediatamente
- Retry 1: 5 segundos
- Retry 2: 15 segundos  
- Retry 3: 60 segundos
- Subsequentes: a cada 5 minutos

O UUID do canhoto é gerado no device antes da sincronização. Isso garante idempotência:
mesmo que o app envie o mesmo canhoto duas vezes (ex: timeout onde o servidor já gravou),
o campo `UNIQUE(entrega_id, device_id)` no banco previne duplicidade.

## Consequências

**Positivas:**
- Experiência do motorista não depende de rede
- Capacidade de trabalhar offline por até 8h (requisito não-funcional atendido)
- Dados nunca perdidos mesmo com queda de bateria durante upload

**Negativas (dívida técnica assumida):**
- Possibilidade de conflito: dois devices tentando sincronizar o mesmo canhoto
  (ex: motorista tirou foto, auxiliar tirou outra). Tratado via `UNIQUE(entrega_id, device_id)` —
  mas revisão manual pode ser necessária se ambas as fotos chegarem.
- Relógio do device incorreto: `capturado_em` depende do relógio do device. Se o motorista
  manipular a hora manualmente, o timestamp pode ser incorreto. Mitigação: validar que
  `capturado_em` não seja mais de 24h no futuro nem mais de 7 dias no passado.
