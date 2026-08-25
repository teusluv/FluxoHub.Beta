package br.com.pod.canhotos;

import br.com.pod.auditoria.AuditoriaService;
import br.com.pod.domain.canhoto.Canhoto;
import br.com.pod.domain.canhoto.CanhotoRepository;
import br.com.pod.domain.canhoto.CanhotoResponse;
import br.com.pod.domain.canhoto.UploadCanhotoRequest;
import br.com.pod.domain.entrega.Entrega;
import br.com.pod.domain.entrega.StatusEntrega;
import br.com.pod.domain.usuario.Usuario;
import br.com.pod.entregas.EntregaService;
import br.com.pod.shared.exception.PodException;
import br.com.pod.shared.security.SecurityUtils;
import br.com.pod.shared.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de canhotos — orquestra upload, OCR assíncrono e atualização de entrega.
 *
 * <p><strong>Fluxo de upload:</strong>
 * <ol>
 *   <li>Recebe imagem via multipart.</li>
 *   <li>Verifica idempotência (entregaId + deviceId já existe?).</li>
 *   <li>Valida que a entrega pertence ao tenant correto.</li>
 *   <li>Faz upload para MinIO/S3 e obtém caminho relativo.</li>
 *   <li>Persiste o {@link Canhoto} com status "aguardando OCR".</li>
 *   <li>Atualiza o status da entrega para {@code ENTREGUE_COM_CANHOTO}.</li>
 *   <li>Dispara OCR assíncrono em background — não bloqueia a resposta.</li>
 *   <li>Retorna {@link CanhotoResponse} com URL pré-assinada da imagem.</li>
 * </ol>
 *
 * <p><strong>Idempotência de batch sync:</strong> se o mobile tentar sincronizar o
 * mesmo canhoto duas vezes (retry após timeout), o segundo request retorna o registro
 * existente sem criar duplicata. Isso é garantido pelo constraint
 * {@code UNIQUE(entrega_id, device_id)} e verificado antes do upload.
 */
@Service
public class CanhotoService {

    private static final Logger log = LoggerFactory.getLogger(CanhotoService.class);

    private final CanhotoRepository canhotoRepository;
    private final StorageService storageService;
    private final OcrService ocrService;
    private final EntregaService entregaService;
    private final AuditoriaService auditoriaService;

    public CanhotoService(CanhotoRepository canhotoRepository,
                          StorageService storageService,
                          OcrService ocrService,
                          EntregaService entregaService,
                          AuditoriaService auditoriaService) {
        this.canhotoRepository = canhotoRepository;
        this.storageService = storageService;
        this.ocrService = ocrService;
        this.entregaService = entregaService;
        this.auditoriaService = auditoriaService;
    }

    // =========================================================================
    // UPLOAD DE CANHOTO (único)
    // =========================================================================

    /**
     * Processa upload de um canhoto.
     *
     * <p>Idempotente: se (entregaId + deviceId) já existe, retorna o registro
     * existente sem fazer nada — safe para retries do mobile.
     */
    @Transactional
    public CanhotoResponse upload(UploadCanhotoRequest req) {
        Usuario usuario = SecurityUtils.getCurrentUsuario();

        // 1. Idempotência: evitar duplicata em retry
        Optional<Canhoto> existente = canhotoRepository
            .findByEntregaIdAndDeviceId(req.entregaId(), req.deviceId());
        if (existente.isPresent()) {
            log.info("Canhoto já existe (idempotência): entrega={} device={} — retornando existente",
                req.entregaId(), req.deviceId());
            return toResponse(existente.get());
        }

        // 2. Valida entrega e tenant
        Entrega entrega = entregaService.encontrarPorIdComTenant(req.entregaId());

        // 3. Valida que o motorista só pode enviar canhotos de suas próprias entregas
        if (SecurityUtils.isMotorista()) {
            if (entrega.getMotorista() == null ||
                !entrega.getMotorista().getId().equals(usuario.getId())) {
                throw new PodException.AcessoNegado(
                    "Você só pode registrar canhotos de suas próprias entregas");
            }
        }

        // 4. Valida duplicidade de canhoto válido
        long canhotosValidos = canhotoRepository.countByEntregaIdAndValidoTrue(req.entregaId());
        if (canhotosValidos > 0) {
            // Avisa mas não bloqueia — pode ser re-captura intencional
            log.warn("Entrega {} já possui canhoto válido — nova captura será registrada", req.entregaId());
        }

        // 5. Upload para S3/MinIO
        String caminhoImagem = storageService.upload(req.imagem());

        // 6. Persiste o canhoto
        Canhoto canhoto = Canhoto.builder()
            .entrega(entrega)
            .urlImagem(caminhoImagem)
            .deviceId(req.deviceId())
            .capturadoEm(req.capturedAt() != null ? req.capturedAt() : OffsetDateTime.now())
            .sincronizadoEm(OffsetDateTime.now())
            .necessitaRevisao(true) // inicia como necessita revisão — OCR atualiza depois
            .valido(true)
            .build();

        canhoto = canhotoRepository.save(canhoto);

        // 7. Atualiza status da entrega → ENTREGUE_COM_CANHOTO
        entregaService.atualizarStatusInterno(
            entrega.getId(), StatusEntrega.ENTREGUE_COM_CANHOTO, entrega.getFilial());

        // 8. Auditoria
        auditoriaService.registrar(
            "canhoto", canhoto.getId(), AuditoriaService.CANHOTO_SINCRONIZADO,
            usuario, entrega.getFilial(),
            AuditoriaService.detalhes(
                "entregaId", entrega.getId(),
                "numeroNota", entrega.getNumeroNotaFiscal(),
                "deviceId", req.deviceId(),
                "capturadoEm", req.capturedAt()
            )
        );

        log.info("Canhoto registrado: id={} entrega={} nf={} device={}",
            canhoto.getId(), entrega.getId(), entrega.getNumeroNotaFiscal(), req.deviceId());

        // 9. Dispara OCR assíncrono (não bloqueia resposta)
        try {
            byte[] imagemBytes = req.imagem().getBytes();
            processarOcrAsync(canhoto.getId(), imagemBytes);
        } catch (IOException e) {
            log.warn("Não foi possível ler bytes da imagem para OCR — canhoto permanecerá em revisão: {}", e.getMessage());
        }

        return toResponse(canhoto);
    }

    /**
     * Batch sync do mobile — processa múltiplos canhotos de uma vez.
     *
     * <p>Cada item é processado de forma independente. Falha em um não cancela os outros.
     * Retorna lista de resultados com sucesso/erro por item.
     */
    @Transactional
    public BatchSyncResponse batchSync(List<UploadCanhotoRequest> items) {
        var resultados = items.stream().map(item -> {
            try {
                CanhotoResponse resp = upload(item);
                return BatchSyncResponse.Item.sucesso(item.entregaId(), resp.id());
            } catch (Exception e) {
                log.warn("Falha no batch sync item entregaId={}: {}", item.entregaId(), e.getMessage());
                return BatchSyncResponse.Item.erro(item.entregaId(), e.getMessage());
            }
        }).toList();

        long sucessos = resultados.stream().filter(BatchSyncResponse.Item::ok).count();
        log.info("Batch sync concluído: {}/{} com sucesso", sucessos, items.size());

        return new BatchSyncResponse(resultados, (int) sucessos, items.size() - (int) sucessos);
    }

    // =========================================================================
    // CONSULTA
    // =========================================================================

    /** Busca canhotos válidos de uma entrega, com URL pré-assinada. */
    @Transactional(readOnly = true)
    public List<CanhotoResponse> buscarPorEntrega(UUID entregaId) {
        // Valida tenant
        entregaService.encontrarPorIdComTenant(entregaId);

        return canhotoRepository
            .findByEntregaIdAndValidoTrueOrderByCapturadoEmDesc(entregaId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /** Busca canhoto por ID com URL pré-assinada. */
    @Transactional(readOnly = true)
    public CanhotoResponse buscarPorId(UUID id) {
        Canhoto canhoto = canhotoRepository.findById(id)
            .orElseThrow(() -> new PodException.NaoEncontrado("Canhoto", id));

        // Valida acesso ao tenant da entrega vinculada
        UUID filialId = TenantContext.getFilialId();
        if (filialId != null &&
            !canhoto.getEntrega().getFilial().getId().equals(filialId)) {
            throw new PodException.AcessoNegado("Acesso negado ao canhoto");
        }

        return toResponse(canhoto);
    }

    // =========================================================================
    // INVALIDAÇÃO
    // =========================================================================

    /** Invalida um canhoto com motivo (admin/vendedor apenas). */
    @Transactional
    public CanhotoResponse invalidar(UUID id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new PodException.Invalido("Motivo de invalidação é obrigatório");
        }

        Canhoto canhoto = canhotoRepository.findById(id)
            .orElseThrow(() -> new PodException.NaoEncontrado("Canhoto", id));

        if (!canhoto.isValido()) {
            throw new PodException.Conflito("Canhoto já está invalidado");
        }

        canhoto.setValido(false);
        canhoto.setMotivoInvalidacao(motivo.trim());
        canhoto = canhotoRepository.save(canhoto);

        Usuario usuario = SecurityUtils.getCurrentUsuario();
        auditoriaService.registrar(
            "canhoto", canhoto.getId(), AuditoriaService.CANHOTO_INVALIDADO,
            usuario, canhoto.getEntrega().getFilial(),
            AuditoriaService.detalhes("motivo", motivo)
        );

        log.info("Canhoto {} invalidado por {} — motivo: {}", id, usuario.getId(), motivo);
        return toResponse(canhoto);
    }

    // =========================================================================
    // OCR ASSÍNCRONO (background)
    // =========================================================================

    /**
     * Processa OCR da imagem em background após o upload.
     *
     * <p>Executado no pool {@code ocrTaskExecutor}. O resultado é gravado de volta
     * no canhoto via nova transação. O endpoint de upload já retornou neste ponto.
     */
    @Async("ocrTaskExecutor")
    public void processarOcrAsync(UUID canhotoId, byte[] imagemBytes) {
        log.info("OCR assíncrono iniciado para canhoto={}", canhotoId);
        try {
            OcrService.ResultadoOcr resultado = ocrService.extrairTexto(imagemBytes);
            salvarResultadoOcr(canhotoId, resultado);
        } catch (Exception e) {
            log.error("OCR assíncrono falhou para canhoto={}: {}", canhotoId, e.getMessage(), e);
            // Não propaga — o canhoto permanece com necessitaRevisao=true
        }
    }

    @Transactional
    public void salvarResultadoOcr(UUID canhotoId, OcrService.ResultadoOcr resultado) {
        canhotoRepository.findById(canhotoId).ifPresent(c -> {
            c.setTextoOcrExtraido(resultado.texto());
            c.setConfiancaOcr(resultado.confianca());
            c.setNecessitaRevisao(resultado.necessitaRevisao());
            canhotoRepository.save(c);

            String acao = resultado.necessitaRevisao()
                ? AuditoriaService.OCR_NECESSITA_REVISAO
                : AuditoriaService.OCR_CONCLUIDO;

            auditoriaService.registrarAsync(
                "canhoto", canhotoId, acao,
                c.getEntrega().getFilial(),
                AuditoriaService.detalhes(
                    "confianca", resultado.confianca(),
                    "necessitaRevisao", resultado.necessitaRevisao(),
                    "chars", resultado.texto() != null ? resultado.texto().length() : 0
                )
            );

            log.info("OCR salvo: canhoto={} confiança={} revisão={}",
                canhotoId, resultado.confianca(), resultado.necessitaRevisao());
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CanhotoResponse toResponse(Canhoto c) {
        String urlPresignada = storageService.gerarUrlPresignada(c.getUrlImagem());
        return CanhotoResponse.de(c, urlPresignada);
    }
}
