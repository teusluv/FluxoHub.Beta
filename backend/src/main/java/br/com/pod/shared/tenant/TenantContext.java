package br.com.pod.shared.tenant;

import java.util.UUID;

/**
 * Contexto de tenant (filial) armazenado em ThreadLocal.
 *
 * <p>O {@link br.com.pod.auth.filter.FilialContextFilter} popula este contexto
 * após autenticação JWT bem-sucedida. Toda query que envolva dados de negócio
 * deve passar pelo filtro Hibernate correspondente, que lê este contexto.
 *
 * <p><strong>CRÍTICO:</strong> o contexto DEVE ser limpo ao final da request
 * (via {@code TenantContext.clear()}) para evitar vazamento entre threads
 * reutilizadas pelo servidor de aplicação.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_FILIAL = new ThreadLocal<>();

    private TenantContext() {}

    public static void setFilialId(UUID filialId) {
        CURRENT_FILIAL.set(filialId);
    }

    public static UUID getFilialId() {
        return CURRENT_FILIAL.get();
    }

    public static boolean hasFilial() {
        return CURRENT_FILIAL.get() != null;
    }

    /** Deve ser chamado no finally de qualquer filter que definir o contexto. */
    public static void clear() {
        CURRENT_FILIAL.remove();
    }
}
