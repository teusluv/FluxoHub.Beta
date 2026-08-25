package br.com.pod.domain.nota;

import br.com.pod.domain.usuario.Papel;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotaResponse(
        UUID id,
        UUID entregaId,
        UUID autorId,
        String autorNome,
        Papel autorPapel,
        UUID filialId,
        TipoNota tipo,
        String conteudo,
        String idempotencyKey,
        OffsetDateTime criadoEm
) {
    public static NotaResponse de(NotaEntrega nota) {
        return new NotaResponse(
                nota.getId(),
                nota.getEntrega().getId(),
                nota.getAutor().getId(),
                nota.getAutorNome(),
                nota.getAutorPapel(),
                nota.getFilial().getId(),
                nota.getTipo(),
                nota.getConteudo(),
                nota.getIdempotencyKey(),
                nota.getCriadoEm()
        );
    }
}
