package br.com.pod.domain.nota;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotaEntregaRepository extends JpaRepository<NotaEntrega, UUID> {

    Page<NotaEntrega> findByEntregaIdAndFilialIdOrderByCriadoEmDesc(
            UUID entregaId, UUID filialId, Pageable pageable);

    Optional<NotaEntrega> findByEntregaIdAndIdempotencyKey(
            UUID entregaId, String idempotencyKey);
}
