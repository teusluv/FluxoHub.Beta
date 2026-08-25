package br.com.pod.auth;

import br.com.pod.config.JwtProperties;
import br.com.pod.domain.usuario.Papel;
import br.com.pod.domain.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Serviço responsável por geração e validação de access tokens JWT.
 *
 * <p>Claims incluídas no access token:
 * <ul>
 *   <li>{@code sub} — ID do usuário (UUID)</li>
 *   <li>{@code email} — email do usuário</li>
 *   <li>{@code papel} — papel (MOTORISTA, VENDEDOR, ADMIN)</li>
 *   <li>{@code filial_id} — UUID da filial</li>
 *   <li>{@code admin_global} — boolean; true apenas para admins corporativos</li>
 * </ul>
 *
 * <p>O refresh token é opaco (UUID random) e gerenciado por {@link RefreshTokenService}.
 * Tokens JWT são stateless; a revogação acontece apenas para refresh tokens via banco.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    public String gerarAccessToken(Usuario usuario) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", usuario.getEmail());
        claims.put("papel", usuario.getPapel().name());
        claims.put("filial_id", usuario.getFilial().getId().toString());
        claims.put("admin_global", usuario.isAdminGlobal());

        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + props.accessExpiryMs()))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extrai todas as claims de um token.
     *
     * @throws JwtException se o token for inválido ou expirado
     */
    public Claims extrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extrairUsuarioId(String token) {
        return UUID.fromString(extrairClaims(token).getSubject());
    }

    public String extrairEmail(String token) {
        return extrairClaims(token).get("email", String.class);
    }

    public Papel extrairPapel(String token) {
        return Papel.valueOf(extrairClaims(token).get("papel", String.class));
    }

    public UUID extrairFilialId(String token) {
        return UUID.fromString(extrairClaims(token).get("filial_id", String.class));
    }

    public boolean extrairAdminGlobal(String token) {
        return Boolean.TRUE.equals(extrairClaims(token).get("admin_global", Boolean.class));
    }

    /**
     * Valida o token sem lançar exceção — retorna false se inválido/expirado.
     * Adequado para uso em filtros onde queremos controle explícito do fluxo.
     */
    public boolean isTokenValido(String token) {
        try {
            extrairClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(props.secret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
