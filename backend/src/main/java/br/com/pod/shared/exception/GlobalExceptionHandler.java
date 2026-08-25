package br.com.pod.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Handler global de exceções.
 *
 * <p>Usa {@link ProblemDetail} (RFC 9457 / HTTP Problem Details) como formato
 * de resposta — padrão moderno, compatível com Spring Boot 3 e cliente HTTP
 * que saiba interpretar application/problem+json.
 *
 * <p>NUNCA retorna stack trace para o cliente. Erros inesperados são logados
 * com nível ERROR e retornam 500 genérico.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Erros de domínio ------------------------------------------------

    @ExceptionHandler(PodException.class)
    public ResponseEntity<ProblemDetail> handlePodException(PodException ex,
                                                             HttpServletRequest req) {
        log.warn("Erro de domínio [{}]: {}", ex.getStatus().value(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(problem(ex.getStatus(), ex.getMessage(), req));
    }

    // ---- Autenticação / Autorização --------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex,
                                                               HttpServletRequest req) {
        log.warn("Falha de autenticação: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(problem(HttpStatus.UNAUTHORIZED, "Credenciais inválidas ou token expirado", req));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex,
                                                               HttpServletRequest req) {
        // Mensagem genérica para não revelar qual campo está errado (segurança)
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(problem(HttpStatus.UNAUTHORIZED, "Email ou senha incorretos", req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest req) {
        log.warn("Acesso negado para {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "Você não tem permissão para esta operação", req));
    }

    // ---- Validação de input ----------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest req) {
        String campos = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> "%s: %s".formatted(e.getField(), e.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        log.debug("Validação falhou: {}", campos);
        var pd = problem(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos", req);
        pd.setProperty("campos", campos);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                    HttpServletRequest req) {
        String violations = ex.getConstraintViolations().stream()
                .map(v -> "%s: %s".formatted(v.getPropertyPath(), v.getMessage()))
                .collect(Collectors.joining("; "));
        var pd = problem(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos", req);
        pd.setProperty("campos", violations);
        return ResponseEntity.badRequest().body(pd);
    }

    // ---- Erros inesperados -----------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex,
                                                           HttpServletRequest req) {
        // Log completo com stack trace — mas NUNCA exposto ao cliente
        log.error("Erro inesperado em {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Ocorreu um erro interno. Tente novamente ou contate o suporte.", req));
    }

    // ---- Utilitário -------------------------------------------------------

    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest req) {
        var pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://pod.com.br/errors/" + status.value()));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
