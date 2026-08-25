package br.com.pod.domain.entrega;

/**
 * Status do ciclo de vida de uma entrega.
 *
 * <p>Transições válidas:
 * <pre>
 * PENDENTE ──► EM_ROTA ──► ENTREGUE_SEM_CANHOTO ──► ENTREGUE_COM_CANHOTO
 *                │                  │
 *                └──────────────────┴──► DIVERGENCIA (ADMIN pode marcar a qualquer ponto)
 * </pre>
 *
 * <p>A transição para ENTREGUE_COM_CANHOTO é feita automaticamente
 * pelo sistema após o OCR confirmar o canhoto (Fase 3).
 */
public enum StatusEntrega {
    PENDENTE,
    EM_ROTA,
    ENTREGUE_SEM_CANHOTO,
    ENTREGUE_COM_CANHOTO,
    DIVERGENCIA
}
