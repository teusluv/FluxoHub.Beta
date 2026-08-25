package br.com.pod.domain.canhoto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de canhotos.
 *
 * <p>Não expõe métodos de DELETE — canhotos são imutáveis por design.
 */
public interface CanhotoRepository extends JpaRepository<Canhoto, UUID> {

    /**
     * Busca canhotos válidos de uma entrega (pode haver múltiplos em caso de
     * re-sincronização ou captura duplicada marcada como inválida).
     */
    List<Canhoto> findByEntregaIdAndValidoTrueOrderByCapturadoEmDesc(UUID entregaId);

    /**
     * Verifica idempotência: se já existe canhoto para esta entrega + device.
     * Usado pelo batch sync para evitar duplicidade em retries.
     */
    Optional<Canhoto> findByEntregaIdAndDeviceId(UUID entregaId, String deviceId);

    /**
     * Lista canhotos que necessitam de revisão manual (OCR com baixa confiança).
     * Usado pelo dashboard admin (Fase 7).
     */
    @Query("SELECT c FROM Canhoto c WHERE c.necessitaRevisao = true AND c.valido = true " +
           "ORDER BY c.sincronizadoEm ASC")
    List<Canhoto> findPendentesRevisao();

    /** Conta canhotos válidos de uma entrega — usado para verificar duplicidade. */
    long countByEntregaIdAndValidoTrue(UUID entregaId);
}
