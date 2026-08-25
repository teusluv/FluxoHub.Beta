package br.com.pod.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção base do domínio POD.
 *
 * <p>Subclasses representam erros de negócio com HTTP status semântico.
 * O {@link GlobalExceptionHandler} mapeia estas exceções para respostas
 * padronizadas — nunca vazar stack traces para o cliente.
 */
public class PodException extends RuntimeException {

    private final HttpStatus status;

    public PodException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public PodException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    // ---- Subclasses para casos de uso específicos ----

    public static class NaoEncontrado extends PodException {
        public NaoEncontrado(String recurso, Object id) {
            super("%s não encontrado: %s".formatted(recurso, id), HttpStatus.NOT_FOUND);
        }
    }

    public static class AcessoNegado extends PodException {
        public AcessoNegado(String motivo) {
            super(motivo, HttpStatus.FORBIDDEN);
        }
    }

    public static class Conflito extends PodException {
        public Conflito(String message) {
            super(message, HttpStatus.CONFLICT);
        }
    }

    public static class Invalido extends PodException {
        public Invalido(String message) {
            super(message, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public static class TokenInvalido extends PodException {
        public TokenInvalido(String message) {
            super(message, HttpStatus.UNAUTHORIZED);
        }
    }
}
