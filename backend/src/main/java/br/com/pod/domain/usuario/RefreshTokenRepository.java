package br.com.pod.domain.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoga todos os tokens de um usuário — usado em logout de todos os dispositivos. */
    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revogado = true, rt.revogadoEm = :agora
        WHERE rt.usuario.id = :usuarioId AND rt.revogado = false
    """)
    int revogarTodosPorUsuario(UUID usuarioId, OffsetDateTime agora);

    /** Limpeza periódica de tokens expirados (job agendado na Fase 7). */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiraEm < :agora")
    int deletarExpirados(OffsetDateTime agora);
}
