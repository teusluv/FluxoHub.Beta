package br.com.pod.admin;

import br.com.pod.domain.entrega.Entrega;
import br.com.pod.domain.entrega.StatusEntrega;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AlertasJob {

    private static final Logger log = LoggerFactory.getLogger(AlertasJob.class);

    private final EntityManager em;
    private final int hoursWithoutCanhoto;

    public AlertasJob(
            EntityManager em,
            @Value("${pod.alerts.hours-without-canhoto:6}") int hoursWithoutCanhoto) {
        this.em = em;
        this.hoursWithoutCanhoto = hoursWithoutCanhoto;
    }

    /**
     * Roda a cada hora no minuto 0.
     * Busca entregas que estão "ENTREGUE_SEM_CANHOTO" há mais de X horas.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    public void detectarEntregasSemCanhoto() {
        log.info("Iniciando job de detecção de anomalias (entregas sem canhoto > {} horas)", hoursWithoutCanhoto);

        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusHours(hoursWithoutCanhoto);

        List<Entrega> anomalias = em.createQuery(
                "SELECT e FROM Entrega e WHERE e.status = :status AND e.dataEntregaReal < :threshold", Entrega.class)
                .setParameter("status", StatusEntrega.ENTREGUE_SEM_CANHOTO)
                .setParameter("threshold", threshold)
                .getResultList();

        if (anomalias.isEmpty()) {
            log.info("Nenhuma anomalia detectada.");
            return;
        }

        // Em um cenário real, aqui dispararíamos e-mail, notificação pro gestor, ou salvaríamos num BD de Alertas.
        log.warn("ATENÇÃO: {} entregas estão sem canhoto há mais de {} horas!", anomalias.size(), hoursWithoutCanhoto);
        for (Entrega e : anomalias) {
            log.warn("Alerta de Risco -> NF: {} | Motorista: {} | Filial: {} | Entregue em: {}", 
                    e.getNumeroNotaFiscal(), 
                    e.getMotorista() != null ? e.getMotorista().getNome() : "N/A",
                    e.getFilial().getNome(),
                    e.getDataEntregaReal());
        }
    }
}
