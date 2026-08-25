package br.com.pod.domain.canhoto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response de um canhoto, com URL pré-assinada de acesso à imagem.
 *
 * <p>A {@code urlImagem} é sempre uma URL pré-assinada com expiração de 15min.
 * Nunca é armazenada no banco — é gerada dinamicamente pelo {@link CanhotoService}.
 */
public record CanhotoResponse(
        UUID id,
        UUID entregaId,
        String urlImagem,            // URL pré-assinada (15min de validade)
        String textoOcrExtraido,
        BigDecimal confiancaOcr,
        boolean necessitaRevisao,
        boolean valido,
        String motivoInvalidacao,
        OffsetDateTime capturadoEm,
        OffsetDateTime sincronizadoEm,
        String deviceId
) {
    public static CanhotoResponse de(Canhoto c, String urlPresignada) {
        return new CanhotoResponse(
                c.getId(),
                c.getEntrega().getId(),
                urlPresignada,
                c.getTextoOcrExtraido(),
                c.getConfiancaOcr(),
                c.isNecessitaRevisao(),
                c.isValido(),
                c.getMotivoInvalidacao(),
                c.getCapturadoEm(),
                c.getSincronizadoEm(),
                c.getDeviceId()
        );
    }
}
