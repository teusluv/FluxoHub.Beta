package br.com.pod.shared.security;

import br.com.pod.domain.usuario.Papel;
import br.com.pod.domain.usuario.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilitários para acessar o usuário autenticado no SecurityContext.
 *
 * <p>Centraliza o acesso ao principal autenticado para evitar duplicação
 * de código nos services. Lança {@link IllegalStateException} se chamado
 * fora de um contexto autenticado — isso não deve acontecer se o Security
 * estiver configurado corretamente.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Usuario getCurrentUsuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }
        throw new IllegalStateException("Nenhum usuário autenticado no contexto — verifique o SecurityConfig");
    }

    public static boolean isPapel(Papel papel) {
        return getCurrentUsuario().getPapel() == papel;
    }

    public static boolean isMotorista() {
        return isPapel(Papel.MOTORISTA);
    }

    public static boolean isAdmin() {
        return isPapel(Papel.ADMIN);
    }

    public static boolean isAdminGlobal() {
        return getCurrentUsuario().isAdminGlobal();
    }
}
