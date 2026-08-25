package br.com.pod.notas;

import br.com.pod.domain.nota.CriarNotaRequest;
import br.com.pod.domain.nota.NotaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * Controller REST para notas e observações complementares vinculadas a entregas.
 */
@RestController
@RequestMapping("/api/v1/entregas/{id}/notas")
@Tag(name = "Notas de Entrega", description = "Gestão de notas, observações e instruções operacionais vinculadas a entregas")
public class NotaEntregaController {

    private final NotaEntregaService notaEntregaService;

    public NotaEntregaController(NotaEntregaService notaEntregaService) {
        this.notaEntregaService = notaEntregaService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('VENDEDOR', 'ADMIN')")
    @Operation(summary = "Cria uma nova nota/observação vinculada a uma entrega (somente VENDEDOR e ADMIN)")
    public ResponseEntity<NotaResponse> criar(
            @Parameter(description = "ID da entrega") @PathVariable UUID id,
            @Valid @RequestBody CriarNotaRequest req) {

        NotaResponse response = notaEntregaService.criar(id, req);
        var location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{notaId}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VENDEDOR', 'ADMIN', 'MOTORISTA')")
    @Operation(summary = "Lista notas vinculadas a uma entrega, paginadas, mais recentes primeiro")
    public ResponseEntity<Page<NotaResponse>> listar(
            @Parameter(description = "ID da entrega") @PathVariable UUID id,
            @ParameterObject @PageableDefault(size = 20, sort = "criadoEm") Pageable pageable) {

        return ResponseEntity.ok(notaEntregaService.listarPorEntrega(id, pageable));
    }
}
