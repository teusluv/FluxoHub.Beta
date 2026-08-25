package br.com.pod.domain.nota;

import br.com.pod.domain.entrega.Entrega;
import br.com.pod.domain.filial.Filial;
import br.com.pod.domain.usuario.Papel;
import br.com.pod.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade NotaEntrega — notas e observações complementares vinculadas a entregas.
 *
 * <p><strong>Invariantes de negócio:</strong>
 * <ul>
 *   <li>Notas são imutáveis após criadas para garantir trilha de auditoria.</li>
 *   <li>Pertence estritamente a uma entrega e a uma filial (multi-tenant).</li>
 *   <li>Autores são restritos a VENDEDOR e ADMIN (motoristas possuem acesso somente leitura).</li>
 * </ul>
 */
@Entity
@Table(
    name = "notas_entrega",
    indexes = {
        @Index(name = "idx_notas_entrega_entrega", columnList = "entrega_id, criado_em DESC"),
        @Index(name = "idx_notas_entrega_filial",  columnList = "filial_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrega_id", nullable = false)
    private Entrega entrega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filial_id", nullable = false)
    private Filial filial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "autor_nome", nullable = false, length = 200)
    private String autorNome;

    @Enumerated(EnumType.STRING)
    @Column(name = "autor_papel", nullable = false, length = 30)
    private Papel autorPapel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TipoNota tipo = TipoNota.GERAL;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = OffsetDateTime.now();
        }
        if (tipo == null) {
            tipo = TipoNota.GERAL;
        }
    }
}
