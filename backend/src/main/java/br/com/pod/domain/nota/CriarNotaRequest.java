package br.com.pod.domain.nota;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarNotaRequest(
        @NotBlank(message = "O conteúdo da nota é obrigatório")
        @Size(max = 2000, message = "O conteúdo da nota deve ter no máximo 2000 caracteres")
        String conteudo,

        TipoNota tipo,

        @Size(max = 64, message = "A chave de idempotência deve ter no máximo 64 caracteres")
        String idempotencyKey
) {
    public CriarNotaRequest {
        if (tipo == null) {
            tipo = TipoNota.GERAL;
        }
    }
}
