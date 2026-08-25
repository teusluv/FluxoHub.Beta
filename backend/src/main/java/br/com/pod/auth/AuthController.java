package br.com.pod.auth;

import br.com.pod.auth.dto.LoginRequest;
import br.com.pod.auth.dto.LoginResponse;
import br.com.pod.auth.dto.RefreshRequest;
import br.com.pod.auth.dto.RefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de autenticação.
 *
 * <p>Todos os endpoints deste controller são acessíveis sem autenticação
 * (configurado no {@link br.com.pod.config.SecurityConfig}).
 *
 * <p>Rate limiting: aplicado via Bucket4j na camada de filtro (Fase 7).
 * Por enquanto, o rate limiting de login está no plano de segurança mas
 * não implementado — dívida técnica documentada.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Login, refresh de token e logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica usuário e retorna par de tokens JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Emite novo access token usando refresh token válido")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoga o refresh token (logout single device)")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
