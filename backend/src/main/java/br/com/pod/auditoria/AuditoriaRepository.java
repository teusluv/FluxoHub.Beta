package br.com.pod.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório de auditoria — SOMENTE leitura e INSERT.
 *
 * <p>Exposição deliberadamente mínima: nenhum método de delete/update
 * deve ser adicionado aqui. Qualquer PR que adicione {@code delete*} ou
 * {@code update*} deve ser rejeitado em code review como violação da
 * política de imutabilidade de auditoria.
 */
public interface AuditoriaRepository extends JpaRepository<EventoAuditoria, UUID> {

    List<EventoAuditoria> findByEntidadeAndEntidadeIdOrderByCriadoEmDesc(
            String entidade, UUID entidadeId);

    List<EventoAuditoria> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);
}
