package br.com.pod.entregas;

import br.com.pod.domain.entrega.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST de entregas.
 *
 * <p>Autorização por endpoint:
 * <ul>
 *   <li>POST /entregas — VENDEDOR ou ADMIN (criar entrega)</li>
 *   <li>GET /entregas — MOTORISTA, VENDEDOR, ADMIN (com isolamento por papel)</li>
 *   <li>GET /entregas/{id} — todos autenticados (com isolamento por papel)</li>
 *   <li>PATCH /entregas/{id}/status — todos autenticados (com validação no service)</li>
 *   <li>GET /entregas/nota/{numero} — VENDEDOR, ADMIN (busca crítica do vendedor)</li>
 *   <li>GET /entregas/dia — MOTORISTA (tela principal do app)</li>
 * </ul>
 *
 * <p>Paginação: usa {@link Pageable} com defaults (20 itens/página, ordem por criadoEm DESC).
 * O cliente pode sobrescrever via query params: {@code ?page=0&size=50&sort=criadoEm,desc}
 */
@RestController
@RequestMapping("/api/v1/entregas")
@Tag(name = "Entregas", description = "Gestão de entregas e consulta por nota fiscal")
public class EntregaController {

    private final EntregaService entregaService;

    public EntregaController(EntregaService entregaService) {
        this.entregaService = entregaService;
    }

    // -------------------------------------------------------------------------
    // CRIAR
    // -------------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAnyAuthority('VENDEDOR', 'ADMIN')")
    @Operation(summary = "Cria uma nova entrega")
    public ResponseEntity<EntregaResponse> criar(@Valid @RequestBody CriarEntregaRequest req) {
        EntregaResponse response = entregaService.criar(req);
        var location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    // -------------------------------------------------------------------------
    // LISTAR
    // -------------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Lista entregas paginadas com filtros opcionais")
    public ResponseEntity<Page<EntregaResponse>> listar(
            @Parameter(description = "ID do vendedor") @RequestParam(required = false) UUID vendedorId,
            @Parameter(description = "ID do motorista") @RequestParam(required = false) UUID motoristaId,
            @Parameter(description = "Status da entrega") @RequestParam(required = false) StatusEntrega status,
            @Parameter(description = "Data de início (yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data de fim (yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @Parameter(description = "Busca por nota fiscal ou nome do cliente")
            @RequestParam(required = false) String busca,
            @ParameterObject @PageableDefault(size = 20, sort = "criadoEm") Pageable pageable) {

        var filtro = new EntregaFiltro(vendedorId, motoristaId, status, dataInicio, dataFim, busca);
        return ResponseEntity.ok(entregaService.listar(filtro, pageable));
    }

    // -------------------------------------------------------------------------
    // DETALHE
    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    @Operation(summary = "Busca entrega por ID")
    public ResponseEntity<EntregaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(entregaService.buscarPorId(id));
    }

    // -------------------------------------------------------------------------
    // BUSCA POR NOTA FISCAL — endpoint crítico: < 300ms p95
    // -------------------------------------------------------------------------

    @GetMapping("/nota/{numero}")
    @PreAuthorize("hasAnyAuthority('VENDEDOR', 'ADMIN', 'MOTORISTA')")
    @Operation(summary = "Busca entregas por número exato de nota fiscal",
               description = "Endpoint crítico de busca — resposta esperada < 300ms via índice de banco. " +
                             "Retorna lista pois pode haver re-entregas com a mesma NF.")
    public ResponseEntity<List<EntregaResponse>> buscarPorNota(
            @PathVariable String numero) {
        return ResponseEntity.ok(entregaService.buscarPorNota(numero));
    }

    // -------------------------------------------------------------------------
    // ENTREGAS DO DIA — tela principal do app mobile
    // -------------------------------------------------------------------------

    @GetMapping("/dia")
    @Operation(summary = "Entregas do dia para um motorista (app mobile)")
    public ResponseEntity<List<EntregaResponse>> entregasDoDia(
            @RequestParam(required = false) UUID motoristaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate data) {
        return ResponseEntity.ok(entregaService.entregasDoDia(
                motoristaId, data != null ? data : LocalDate.now()));
    }

    // -------------------------------------------------------------------------
    // ATUALIZAR STATUS
    // -------------------------------------------------------------------------

    @PatchMapping("/{id}/status")
    @Operation(summary = "Altera o status de uma entrega (validação de máquina de estados no service)")
    public ResponseEntity<EntregaResponse> atualizarStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarStatusRequest req) {
        return ResponseEntity.ok(entregaService.atualizarStatus(id, req));
    }
}
