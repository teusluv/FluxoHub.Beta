package br.com.pod.canhotos;

import java.util.List;
import java.util.UUID;

/**
 * Resposta do batch sync — resume o resultado de múltiplos uploads.
 */
public record BatchSyncResponse(
    List<Item> itens,
    int sucessos,
    int falhas
) {
    public record Item(
        UUID entregaId,
        UUID canhotoId,   // null em caso de erro
        boolean ok,
        String erro       // null em caso de sucesso
    ) {
        public static Item sucesso(UUID entregaId, UUID canhotoId) {
            return new Item(entregaId, canhotoId, true, null);
        }
        public static Item erro(UUID entregaId, String mensagem) {
            return new Item(entregaId, null, false, mensagem);
        }
    }
}
