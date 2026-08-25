package br.com.pod.domain.entrega;

import br.com.pod.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "entrega_eventos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntregaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrega_id", nullable = false)
    private Entrega entrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior")
    private StatusEntrega statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false)
    private StatusEntrega statusNovo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ator_id", nullable = false)
    private Usuario ator;

    @Column(nullable = false, length = 20)
    private String origem; // 'WEB_ADMIN' | 'APP_MOTORISTA' | 'SYSTEM'

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @PrePersist
    void prePersist() {
        criadoEm = OffsetDateTime.now();
    }
}
