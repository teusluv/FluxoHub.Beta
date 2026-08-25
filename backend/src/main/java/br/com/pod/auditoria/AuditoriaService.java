package br.com.pod.auditoria;

import br.com.pod.domain.filial.Filial;
import br.com.pod.domain.usuario.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Serviço de auditoria — registra eventos imutáveis.
 *
 * <p>Métodos disponíveis apenas para INSERT. Nenhum método de remoção ou
 * alteração está exposto. A anotação {@code @Transactional(propagation = REQUIRES_NEW)}
 * garante que o log de auditoria seja persistido mesmo se a transação
 * principal fizer rollback — o rastro deve existir mesmo em caso de erro.
 *
 * <p>Para ações assíncronas (ex: OCR), use {@code registrarAsync()}.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    // Constantes de ação para evitar typos espalhados pelo código
    public static final String ENTREGA_CRIADA          = "ENTREGA_CRIADA";
    public static final String ENTREGA_STATUS_ALTERADO  = "ENTREGA_STATUS_ALTERADO";
    public static final String CANHOTO_CAPTURADO        = "CANHOTO_CAPTURADO";
    public static final String CANHOTO_SINCRONIZADO     = "CANHOTO_SINCRONIZADO";
    public static final String CANHOTO_INVALIDADO       = "CANHOTO_INVALIDADO";
    public static final String OCR_CONCLUIDO            = "OCR_CONCLUIDO";
    public static final String OCR_NECESSITA_REVISAO    = "OCR_NECESSITA_REVISAO";

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    /**
     * Registra um evento de auditoria em uma nova transação.
     *
     * <p>{@code REQUIRES_NEW}: o evento de auditoria é persistido mesmo que
     * a transação chamadora faça rollback. Isso garante rastreabilidade
     * completa, incluindo tentativas com erro.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String entidade,
                          UUID entidadeId,
                          String acao,
                          Usuario usuario,
                          Filial filial,
                          Map<String, Object> detalhes) {
        var evento = EventoAuditoria.builder()
                .entidade(entidade)
                .entidadeId(entidadeId)
                .acao(acao)
                .usuario(usuario)
                .filial(filial)
                .detalhes(detalhes)
                .build();
        auditoriaRepository.save(evento);
        log.debug("Auditoria: {} {} {} por usuário {}",
                acao, entidade, entidadeId,
                usuario != null ? usuario.getId() : "sistema");
    }

    /**
     * Registra um evento de auditoria de forma assíncrona (para uso em jobs de OCR).
     * Usa pool de threads dedicado configurado no {@link br.com.pod.config.AsyncConfig}.
     */
    @Async("ocrTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAsync(String entidade,
                               UUID entidadeId,
                               String acao,
                               Filial filial,
                               Map<String, Object> detalhes) {
        registrar(entidade, entidadeId, acao, null, filial, detalhes);
    }

    /** Builder fluente de detalhes para não espalhar Map.of() pelo código */
    public static Map<String, Object> detalhes(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues deve ter número par de elementos");
        }
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
