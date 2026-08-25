package br.com.pod.domain.entrega;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EntregaEventoRepository extends JpaRepository<EntregaEvento, UUID> {
    List<EntregaEvento> findAllByEntregaIdOrderByCriadoEmAsc(UUID entregaId);
}
