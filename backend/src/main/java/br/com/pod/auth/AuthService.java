package br.com.pod.auth;

import br.com.pod.auth.dto.LoginRequest;
import br.com.pod.auth.dto.LoginResponse;
import br.com.pod.auth.dto.RefreshRequest;
import br.com.pod.auth.dto.RefreshResponse;
import br.com.pod.config.JwtProperties;
import br.com.pod.domain.usuario.Usuario;
import br.com.pod.domain.usuario.UsuarioRepository;
import br.com.pod.shared.exception.PodException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço de autenticação — orquestra login, refresh e logout.
 *
 * <p>Separa a lógica de negócio do controller HTTP. Cada operação é uma
 * transação independente para garantir consistência entre o estado do
 * banco (refresh tokens) e a resposta ao cliente.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       JwtProperties jwtProperties) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Autentica o usuário e retorna os dois tokens.
     *
     * <p>Mensagem de erro genérica intencional: não revelar se o email existe ou não
     * (prevenção de user enumeration).
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmailAndAtivoTrue(request.email())
                .orElseThrow(() -> {
                    log.warn("Tentativa de login com email não encontrado: {}", request.email());
                    return new PodException.TokenInvalido("Email ou senha incorretos");
                });

        if (!passwordEncoder.matches(request.senha(), usuario.getSenhaHash())) {
            log.warn("Senha incorreta para usuário: {}", usuario.getId());
            throw new PodException.TokenInvalido("Email ou senha incorretos");
        }

        String accessToken = jwtService.gerarAccessToken(usuario);
        String refreshToken = refreshTokenService.criar(usuario);

        log.info("Login bem-sucedido para usuário {} ({})", usuario.getId(), usuario.getPapel());

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.accessExpiryMs(),
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPapel(),
                usuario.getFilial().getId(),
                usuario.getFilial().getNome(),
                usuario.isAdminGlobal()
        );
    }

    /**
     * Emite novo access token usando um refresh token válido.
     * O refresh token NÃO é rotacionado — o mesmo refresh token pode ser usado
     * múltiplas vezes até expirar ou ser revogado via logout.
     *
     * <p>Dívida técnica: implementar rotação de refresh token para maior segurança
     * (cada refresh invalida o anterior e emite um novo par). Aceito por ora para
     * simplificar o fluxo do app mobile.
     */
    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        Usuario usuario = refreshTokenService.validarEObterUsuario(request.refreshToken());
        String novoAccessToken = jwtService.gerarAccessToken(usuario);

        log.debug("Access token renovado para usuário {}", usuario.getId());

        return new RefreshResponse(novoAccessToken, jwtProperties.accessExpiryMs());
    }

    /**
     * Revoga o refresh token (logout single device).
     */
    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revogar(request.refreshToken());
    }
}
