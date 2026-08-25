package br.com.pod.auth.filter;

import br.com.pod.auth.JwtService;
import br.com.pod.domain.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro de autenticação JWT — executado uma vez por request.
 *
 * <p>Fluxo:
 * <ol>
 *   <li>Extrai o Bearer token do header Authorization</li>
 *   <li>Valida o token via {@link JwtService}</li>
 *   <li>Carrega o usuário do banco e popula o {@link SecurityContextHolder}</li>
 *   <li>Adiciona {@code user_id} e {@code email} ao MDC para correlação de logs</li>
 * </ol>
 *
 * <p>Se o token for ausente ou inválido, o filtro prossegue sem autenticar —
 * o Spring Security rejeitará a request se a rota exigir autenticação.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                    UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extrairToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtService.isTokenValido(token)) {
            log.debug("Token JWT inválido para request: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Token válido — autenticar se o SecurityContext ainda estiver vazio
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UUID usuarioId = jwtService.extrairUsuarioId(token);
                String email = jwtService.extrairEmail(token);

                usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
                    if (usuario.isEnabled()) {
                        var authToken = new UsernamePasswordAuthenticationToken(
                                usuario,
                                null,
                                usuario.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        // MDC para correlação de logs — aparece em todos os logs desta request
                        MDC.put("user_id", usuarioId.toString());
                        MDC.put("user_email", email);
                        MDC.put("user_papel", usuario.getPapel().name());
                    }
                });
            } catch (Exception e) {
                log.warn("Erro ao processar token JWT: {}", e.getMessage());
                // Não relança — deixa o Spring Security rejeitar a request
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Limpar MDC para não vazar entre requests (thread pool reutiliza threads)
            MDC.remove("user_id");
            MDC.remove("user_email");
            MDC.remove("user_papel");
        }
    }

    private String extrairToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
