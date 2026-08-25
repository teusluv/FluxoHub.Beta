package br.com.pod.auth.filter;

import br.com.pod.domain.usuario.Usuario;
import br.com.pod.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de contexto de filial (multi-tenant).
 *
 * <p>Executado APÓS o {@link JwtAuthenticationFilter}. Lê o usuário autenticado
 * do SecurityContext e popula o {@link TenantContext} com o {@code filial_id}.
 *
 * <p>Regras:
 * <ul>
 *   <li>Usuário normal → TenantContext = filial do usuário (isolamento garantido)</li>
 *   <li>ADMIN com adminGlobal=true → TenantContext = null (sem filtro de filial;
 *       pode ver dados de todas as filiais)</li>
 * </ul>
 *
 * <p><strong>CRÍTICO:</strong> o TenantContext é limpo no bloco {@code finally},
 * independentemente de exceções, para evitar vazamento entre threads do pool.
 *
 * <p>O filtro de Hibernate (habilitado nas entidades de domínio) consome o
 * TenantContext para injetar automaticamente o {@code WHERE filial_id = ?}
 * em todas as queries — sem precisar especificar manualmente em cada repository.
 */
@Component
public class FilialContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FilialContextFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Usuario usuario) {

                if (usuario.isAdminGlobal()) {
                    // Admin global: sem filtro de filial (pode consultar qualquer filial)
                    TenantContext.clear();
                    log.debug("Admin global autenticado — sem filtro de filial");
                } else {
                    var filialId = usuario.getFilial().getId();
                    TenantContext.setFilialId(filialId);
                    MDC.put("filial_id", filialId.toString());
                    log.debug("Contexto de filial definido: {}", filialId);
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // OBRIGATÓRIO: limpar ThreadLocal independente do resultado da request
            TenantContext.clear();
            MDC.remove("filial_id");
        }
    }
}
