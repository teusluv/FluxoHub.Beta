package br.com.pod.auth;

import br.com.pod.config.JwtProperties;
import br.com.pod.domain.usuario.RefreshToken;
import br.com.pod.domain.usuario.RefreshTokenRepository;
import br.com.pod.domain.usuario.Usuario;
import br.com.pod.shared.exception.PodException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Gerencia o ciclo de vida dos refresh tokens (opacos, armazenados em banco).
 *
 * <p>O refresh token é um UUID aleatório. Apenas o hash SHA-256 é persistido,
 * protegendo o token em caso de vazamento do banco de dados.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties props;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                JwtProperties props) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.props = props;
    }

    /**
     * Gera um novo refresh token para o usuário e o persiste (hashed).
     *
     * @return o token raw (enviado ao cliente UMA vez, nunca armazenado)
     */
    @Transactional
    public String criar(Usuario usuario) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        RefreshToken entity = RefreshToken.builder()
                .usuario(usuario)
                .tokenHash(tokenHash)
                .expiraEm(OffsetDateTime.now().plusNanos(props.refreshExpiryMs() * 1_000_000L))
                .build();

        refreshTokenRepository.save(entity);
        log.debug("Refresh token criado para usuário {}", usuario.getId());
        return rawToken;
    }

    /**
     * Valida o refresh token e retorna o usuário associado.
     *
     * @throws PodException.TokenInvalido se o token não existir, estiver revogado ou expirado
     */
    @Transactional(readOnly = true)
    public Usuario validarEObterUsuario(String rawToken) {
        String tokenHash = sha256(rawToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new PodException.TokenInvalido("Refresh token não encontrado"));

        if (!refreshToken.isValid()) {
            log.warn("Tentativa de uso de refresh token revogado/expirado para usuário {}",
                    refreshToken.getUsuario().getId());
            throw new PodException.TokenInvalido("Refresh token inválido ou expirado");
        }

        return refreshToken.getUsuario();
    }

    /**
     * Revoga o token (logout single device).
     * Não lança exceção se o token não existir — operação idempotente.
     */
    @Transactional
    public void revogar(String rawToken) {
        String tokenHash = sha256(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
            rt.setRevogado(true);
            rt.setRevogadoEm(OffsetDateTime.now());
            refreshTokenRepository.save(rt);
            log.debug("Refresh token revogado para usuário {}", rt.getUsuario().getId());
        });
    }

    /**
     * Revoga todos os refresh tokens do usuário (logout de todos os dispositivos).
     */
    @Transactional
    public void revogarTodos(UUID usuarioId) {
        int count = refreshTokenRepository.revogarTodosPorUsuario(usuarioId, OffsetDateTime.now());
        log.info("Revogados {} refresh tokens para usuário {}", count, usuarioId);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é garantido pela JVM spec — nunca deve acontecer
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}
