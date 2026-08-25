package br.com.pod.domain.entrega;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de entregas.
 *
 * <p>Estende {@link JpaSpecificationExecutor} para queries dinâmicas compostas
 * via {@link EntregaSpec} — padrão Specification do Spring Data JPA.
 * Isso garante que o filtro de filial (multi-tenant) seja sempre composto
 * junto com os outros filtros, não podendo ser esquecido.
 *
 * <p>Queries nativas adicionais para casos específicos (busca por NF exata)
 * usam JPQL com prepared statements — nunca concatenação de string.
 */
public interface EntregaRepository extends JpaRepository<Entrega, UUID>,
                                            JpaSpecificationExecutor<Entrega> {

    /**
     * Busca exata por número de nota fiscal dentro de uma filial.
     * Usada pelo WhatsApp bot e pela tela de busca do vendedor.
     * O índice idx_entregas_nota_filial garante < 1ms para esta query.
     */
    @Query("""
        SELECT e FROM Entrega e
        LEFT JOIN FETCH e.vendedor
        LEFT JOIN FETCH e.motorista
        WHERE e.filial.id = :filialId
          AND e.numeroNotaFiscal = :numeroNota
        ORDER BY e.criadoEm DESC
    """)
    List<Entrega> findByFilialIdAndNumeroNotaFiscal(UUID filialId, String numeroNota);

    /**
     * Versão para admin global (sem filtro de filial).
     */
    @Query("""
        SELECT e FROM Entrega e
        LEFT JOIN FETCH e.vendedor
        LEFT JOIN FETCH e.motorista
        WHERE e.numeroNotaFiscal = :numeroNota
        ORDER BY e.criadoEm DESC
    """)
    List<Entrega> findByNumeroNotaFiscal(String numeroNota);

    /**
     * Entregas de um motorista para um dia específico — tela principal do app mobile.
     * Retorna em ordem de data prevista para facilitar o roteiro do dia.
     */
    @Query("""
        SELECT e FROM Entrega e
        WHERE e.motorista.id = :motoristaId
          AND e.filial.id = :filialId
          AND e.dataPrevistaEntrega = :data
          AND e.status IN ('PENDENTE', 'EM_ROTA', 'ENTREGUE_SEM_CANHOTO')
        ORDER BY e.dataPrevistaEntrega ASC
    """)
    List<Entrega> findEntregasDoDia(UUID motoristaId, UUID filialId,
                                    java.time.LocalDate data);

    /**
     * Contagem de entregas sem canhoto para o dashboard admin (KPI Fase 7).
     * Parametrizado por filialId para suportar admin global (NULL = todas as filiais).
     */
    @Query("""
        SELECT COUNT(e) FROM Entrega e
        WHERE (:filialId IS NULL OR e.filial.id = :filialId)
          AND e.status = 'ENTREGUE_SEM_CANHOTO'
          AND e.dataEntregaReal >= :desde
    """)
    long countEntregasSemCanhoto(UUID filialId, java.time.OffsetDateTime desde);

    Optional<Entrega> findByIdAndFilialId(UUID id, UUID filialId);

    Optional<Entrega> findByFilialIdAndIdempotencyKey(UUID filialId, String idempotencyKey);
}
