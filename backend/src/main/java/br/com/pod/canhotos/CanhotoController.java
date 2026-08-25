package br.com.pod.canhotos;

import br.com.pod.domain.canhoto.CanhotoResponse;
import br.com.pod.domain.canhoto.UploadCanhotoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controller de canhotos — Fase 3.
 *
 * <p><strong>Segurança por endpoint:</strong>
 * <ul>
 *   <li>POST /upload — MOTORISTA, ADMIN</li>
 *   <li>POST /batch-sync — MOTORISTA, ADMIN</li>
 *   <li>GET /entrega/{id} — MOTORISTA (suas), VENDEDOR, ADMIN</li>
 *   <li>GET /{id} — VENDEDOR, ADMIN</li>
 *   <li>PATCH /{id}/invalidar — ADMIN, VENDEDOR</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/canhotos")
@Tag(name = "Canhotos", description = "Upload, sincronização e consulta de canhotos de entrega")
public class CanhotoController {

    private final CanhotoService canhotoService;

    public CanhotoController(CanhotoService canhotoService) {
        this.canhotoService = canhotoService;
    }

    // =========================================================================
    // UPLOAD ÚNICO (mobile individual)
    // =========================================================================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MOTORISTA', 'ADMIN')")
    @Operation(
        summary = "Fazer upload de um canhoto",
        description = "Idempotente via (entregaId + deviceId). " +
                      "OCR processado assincronamente após retorno.")
    public CanhotoResponse upload(
            @RequestParam @NotNull UUID entregaId,
            @RequestParam @NotBlank String deviceId,
            @RequestParam(required = false) OffsetDateTime capturedAt,
            @RequestParam("imagem") @NotNull MultipartFile imagem
    ) {
        if (imagem.isEmpty()) {
            throw new br.com.pod.shared.exception.PodException.Invalido("Imagem não pode ser vazia");
        }
        validateImageType(imagem);

        var req = new UploadCanhotoRequest(entregaId, deviceId,
            capturedAt != null ? capturedAt : OffsetDateTime.now(), imagem);
        return canhotoService.upload(req);
    }

    // =========================================================================
    // BATCH SYNC (mobile — sincroniza fila offline de uma vez)
    // =========================================================================

    /**
     * Sincroniza múltiplos canhotos em um único request.
     *
     * <p>Aceita {@code multipart/form-data} com campos repetidos para cada item.
     * Cada item é processado independentemente — falha em um não cancela os outros.
     */
    @PostMapping(value = "/batch-sync", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.MULTI_STATUS)
    @PreAuthorize("hasAnyAuthority('MOTORISTA', 'ADMIN')")
    @Operation(
        summary = "Sincronização em batch da fila offline",
        description = "Processa múltiplos canhotos de uma vez. " +
                      "Falha parcial retorna HTTP 207 com detalhes por item.")
    public ResponseEntity<BatchSyncResponse> batchSync(
            @RequestParam List<UUID> entregaIds,
            @RequestParam List<String> deviceIds,
            @RequestParam(required = false) List<OffsetDateTime> capturedAts,
            @RequestParam("imagens") List<MultipartFile> imagens
    ) {
        if (entregaIds.size() != deviceIds.size() || entregaIds.size() != imagens.size()) {
            throw new br.com.pod.shared.exception.PodException.Invalido(
                "entregaIds, deviceIds e imagens devem ter o mesmo número de elementos");
        }

        List<UploadCanhotoRequest> requests = new ArrayList<>();
        for (int i = 0; i < entregaIds.size(); i++) {
            validateImageType(imagens.get(i));
            OffsetDateTime captured = (capturedAts != null && i < capturedAts.size())
                ? capturedAts.get(i) : OffsetDateTime.now();
            requests.add(new UploadCanhotoRequest(
                entregaIds.get(i), deviceIds.get(i), captured, imagens.get(i)));
        }

        BatchSyncResponse resp = canhotoService.batchSync(requests);

        // 207 se houve falhas parciais, 201 se tudo ok
        HttpStatus status = resp.falhas() > 0 ? HttpStatus.MULTI_STATUS : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resp);
    }

    // =========================================================================
    // CONSULTA
    // =========================================================================

    @GetMapping("/entrega/{entregaId}")
    @PreAuthorize("hasAnyAuthority('MOTORISTA', 'VENDEDOR', 'ADMIN')")
    @Operation(summary = "Lista canhotos válidos de uma entrega")
    public List<CanhotoResponse> porEntrega(@PathVariable UUID entregaId) {
        return canhotoService.buscarPorEntrega(entregaId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('VENDEDOR', 'ADMIN')")
    @Operation(summary = "Busca canhoto por ID (com URL pré-assinada)")
    public CanhotoResponse porId(@PathVariable UUID id) {
        return canhotoService.buscarPorId(id);
    }

    // =========================================================================
    // INVALIDAÇÃO
    // =========================================================================

    @PatchMapping("/{id}/invalidar")
    @PreAuthorize("hasAnyAuthority('VENDEDOR', 'ADMIN')")
    @Operation(
        summary = "Invalida um canhoto",
        description = "Canhoto não é deletado — marcado como inválido com motivo obrigatório. " +
                      "Registro de auditoria gerado automaticamente.")
    public CanhotoResponse invalidar(
            @PathVariable UUID id,
            @RequestBody InvalidarCanhotoRequest req
    ) {
        return canhotoService.invalidar(id, req.motivo());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void validateImageType(MultipartFile arquivo) {
        String ct = arquivo.getContentType();
        if (ct == null || (!ct.startsWith("image/jpeg") && !ct.startsWith("image/png")
            && !ct.startsWith("image/webp") && !ct.startsWith("image/heic"))) {
            throw new br.com.pod.shared.exception.PodException.Invalido(
                "Formato de imagem não suportado. Use JPEG, PNG, WebP ou HEIC.");
        }
    }

    /** Request body para invalidação. */
    public record InvalidarCanhotoRequest(@NotBlank String motivo) {}
}
