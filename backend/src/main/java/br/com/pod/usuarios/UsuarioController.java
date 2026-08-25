package br.com.pod.usuarios;

import br.com.pod.domain.usuario.Papel;
import br.com.pod.domain.usuario.Usuario;
import br.com.pod.domain.usuario.UsuarioRepository;
import br.com.pod.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuários", description = "Endpoints de gerenciamento e consulta de usuários")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    public UsuarioController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/motoristas")
    @PreAuthorize("hasAnyAuthority('VENDEDOR', 'ADMIN')")
    @Operation(summary = "Lista todos os motoristas ativos da filial")
    public ResponseEntity<List<UsuarioResponse>> listarMotoristas(
            @RequestParam(required = false) UUID filialId) {
        Usuario usuario = SecurityUtils.getCurrentUsuario();
        
        UUID targetFilialId = filialId;
        if (targetFilialId == null || !usuario.isAdminGlobal()) {
            targetFilialId = usuario.getFilial().getId();
        }

        List<Usuario> motoristas = usuarioRepository.findAllByFilialIdAndPapelAndAtivoTrue(
                targetFilialId, Papel.MOTORISTA);

        List<UsuarioResponse> response = motoristas.stream()
                .map(m -> new UsuarioResponse(m.getId(), m.getNome(), m.getEmail()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    public record UsuarioResponse(UUID id, String nome, String email) {}
}
