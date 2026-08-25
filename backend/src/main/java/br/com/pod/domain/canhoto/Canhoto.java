package br.com.pod.domain.canhoto;

import br.com.pod.domain.entrega.Entrega;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade Canhoto — documento fiscal de prova de entrega.
 *
 * <p><strong>Invariantes de negócio:</strong>
 * <ul>
 *   <li>Canhotos NUNCA são deletados — registros inválidos têm {@code valido=FALSE}.</li>
 *   <li>{@code capturado_em} é o timestamp do device (hora real da entrega).</li>
 *   <li>{@code sincronizado_em} é quando o dado chegou ao servidor.</li>
 *   <li>A restrição {@code UNIQUE(entrega_id, device_id)} garante idempotência
 *       de sincronização — retry não cria duplicatas.</li>
 * </ul>
 *
 * <p><strong>URL de imagem:</strong> armazena apenas o caminho relativo no bucket
 * (ex: {@code canhotos/2024/01/abc123.jpg}). URLs pré-assinadas são geradas
 * sob demanda com expiração de 15min pelo {@link CanhotoService}.
 */
@Entity
@Table(
    name = "canhotos",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_canhoto_entrega_device",
        columnNames = {"entrega_id", "device_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Canhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrega_id", nullable = false)
    private Entrega entrega;

    /**
     * Caminho relativo no bucket S3/MinIO.
     * Formato: {@code canhotos/{ano}/{mes}/{uuid}.jpg}
     * Nunca é URL pública — sempre via presigned URL.
     */
    @Column(name = "url_imagem", nullable = false, length = 500)
    private String urlImagem;

    /** Texto extraído pelo OCR (pode ser null se OCR ainda não processou). */
    @Column(name = "texto_ocr_extraido", columnDefinition = "TEXT")
    private String textoOcrExtraido;

    /**
     * Confiança do OCR, de 0.000 a 1.000.
     * Null = OCR ainda não executado ou imagem ilegível.
     */
    @Column(name = "confianca_ocr", precision = 4, scale = 3)
    private BigDecimal confiancaOcr;

    /**
     * TRUE quando confiança OCR < threshold configurado (padrão: 70%).
     * Canhoto marcado para revisão manual aparece no dashboard admin.
     */
    @Column(name = "necessita_revisao", nullable = false)
    @Builder.Default
    private boolean necessitaRevisao = false;

    /**
     * FALSE = canhoto invalidado (nunca deletado).
     * Quando FALSE, {@code motivoInvalidacao} deve estar preenchido.
     */
    @Column(name = "valido", nullable = false)
    @Builder.Default
    private boolean valido = true;

    @Column(name = "motivo_invalidacao", columnDefinition = "TEXT")
    private String motivoInvalidacao;

    /**
     * Timestamp do device no momento da captura.
     * Este é o horário legal da entrega, independente do momento de sync.
     */
    @Column(name = "capturado_em", nullable = false)
    private OffsetDateTime capturadoEm;

    /** Timestamp de quando o registro chegou ao servidor. */
    @Column(name = "sincronizado_em", nullable = false)
    @Builder.Default
    private OffsetDateTime sincronizadoEm = OffsetDateTime.now();

    /**
     * Identificador único do dispositivo que capturou.
     * Junto com {@code entrega_id}, garante idempotência no batch sync.
     */
    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;
}
