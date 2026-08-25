package br.com.pod.admin;

import br.com.pod.admin.dto.DashboardKpiResponse;
import br.com.pod.admin.dto.DashboardKpiResponse.RankingMotorista;
import br.com.pod.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final EntityManager em;

    public DashboardService(EntityManager em) {
        this.em = em;
    }

    @Transactional(readOnly = true)
    public DashboardKpiResponse getKpisDoDia() {
        UUID filialId = TenantContext.getFilialId(); // Admin pode ser global (null) ou de filial
        
        OffsetDateTime inicioDoDia = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime fimDoDia = inicioDoDia.plusDays(1).minusNanos(1);

        String tenantFilter = filialId != null ? " AND e.filial.id = :filialId " : "";

        // 1. Total de Entregas do dia
        Long totalEntregas = em.createQuery(
                "SELECT COUNT(e) FROM Entrega e WHERE e.criadoEm >= :inicio AND e.criadoEm <= :fim" + tenantFilter, Long.class)
                .setParameter("inicio", inicioDoDia)
                .setParameter("fim", fimDoDia)
                .setParameter("filialId", filialId)
                .getSingleResult();
        if (filialId == null) em.createQuery("SELECT 1 FROM Entrega e").setMaxResults(1); // just avoiding missing parameter exceptions if we rewrite it

        // Rewriting to avoid parameter issues when filialId is null
        var qTotal = em.createQuery("SELECT COUNT(e) FROM Entrega e WHERE e.criadoEm >= :inicio AND e.criadoEm <= :fim" + tenantFilter, Long.class)
                .setParameter("inicio", inicioDoDia)
                .setParameter("fim", fimDoDia);
        if (filialId != null) qTotal.setParameter("filialId", filialId);
        totalEntregas = qTotal.getSingleResult();

        // 2. Total Entregas COM CANHOTO
        var qCanhoto = em.createQuery(
                "SELECT COUNT(e) FROM Entrega e WHERE e.criadoEm >= :inicio AND e.criadoEm <= :fim AND e.status = 'ENTREGUE_COM_CANHOTO'" + tenantFilter, Long.class)
                .setParameter("inicio", inicioDoDia)
                .setParameter("fim", fimDoDia);
        if (filialId != null) qCanhoto.setParameter("filialId", filialId);
        Long entregasComCanhoto = qCanhoto.getSingleResult();

        // 3. Tempo médio de Sincronização (Minutos)
        var qTempo = em.createQuery(
                "SELECT AVG(EXTRACT(EPOCH FROM (c.sincronizadoEm - c.capturadoEm)) / 60) " +
                "FROM Canhoto c JOIN c.entrega e " +
                "WHERE c.sincronizadoEm >= :inicio AND c.sincronizadoEm <= :fim" + tenantFilter, Double.class)
                .setParameter("inicio", inicioDoDia)
                .setParameter("fim", fimDoDia);
        if (filialId != null) qTempo.setParameter("filialId", filialId);
        Double tempoMedioMinutos = qTempo.getSingleResult();
        if (tempoMedioMinutos == null) tempoMedioMinutos = 0.0;

        // 4. Ranking de Motoristas (Top 5)
        var qRanking = em.createQuery(
                "SELECT e.motorista.id, e.motorista.nome, COUNT(c) " +
                "FROM Canhoto c JOIN c.entrega e " +
                "WHERE c.sincronizadoEm >= :inicio AND c.sincronizadoEm <= :fim" + tenantFilter +
                "GROUP BY e.motorista.id, e.motorista.nome " +
                "ORDER BY COUNT(c) DESC", Object[].class)
                .setParameter("inicio", inicioDoDia)
                .setParameter("fim", fimDoDia);
        if (filialId != null) qRanking.setParameter("filialId", filialId);
        qRanking.setMaxResults(5);
        
        List<RankingMotorista> ranking = qRanking.getResultList().stream()
                .map(row -> new RankingMotorista(
                        row[0] != null ? row[0].toString() : "N/A", 
                        (String) row[1], 
                        (Long) row[2]))
                .collect(Collectors.toList());

        double percentual = totalEntregas > 0 ? (entregasComCanhoto.doubleValue() / totalEntregas) * 100 : 0.0;

        return new DashboardKpiResponse(
                totalEntregas.intValue(),
                entregasComCanhoto.intValue(),
                Math.round(percentual * 10.0) / 10.0,
                Math.round(tempoMedioMinutos * 10.0) / 10.0,
                ranking
        );
    }
}
