package br.com.pod.domain.usuario;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Refresh token armazenado no banco de dados (blacklist-based revocation).
 *
 * <p>O raw token nunca é armazenado — apenas o hash SHA-256.
 * Isso garante que, mesmo em caso de comprometimento do banco, os tokens
 * não possam ser usados diretamente.
 *
 * <p>Fluxo de revogação:
 * <ol>
 *   <li>Cliente chama POST /auth/logout com o refresh token no body</li>
 *   <li>Backend calcula hash do token e marca {@code revogado = true}</li>
 *   <li>Próxima tentativa de refresh com o mesmo token → rejeitado</li>
 * </ol>
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Hash SHA-256 do token raw (nunca armazenar o token em si). */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(nullable = false)
    @Builder.Default
    private boolean revogado = false;

    @Column(name = "revogado_em")
    private OffsetDateTime revogadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @PrePersist
    void prePersist() {
        criadoEm = OffsetDateTime.now();
    }

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiraEm);
    }

    public boolean isValid() {
        return !revogado && !isExpired();
    }
}
