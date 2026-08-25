package br.com.pod.auth;

import br.com.pod.AbstractIntegrationTest;
import br.com.pod.domain.filial.Filial;
import br.com.pod.domain.filial.FilialRepository;
import br.com.pod.domain.usuario.Papel;
import br.com.pod.domain.usuario.Usuario;
import br.com.pod.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para Auth e RBAC — critério de aceite da Fase 1.
 *
 * <p><strong>Critério principal:</strong> um usuário MOTORISTA não deve conseguir
 * acessar endpoints de admin — validado via HTTP real com JWT real.
 */
@DisplayName("Auth e RBAC — Integração")
class AuthIntegrationIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    FilialRepository filialRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Filial filial;

    @BeforeEach
    void setUp() {
        // Limpa para garantir isolamento entre testes
        usuarioRepository.deleteAll();
        filialRepository.deleteAll();

        filial = filialRepository.save(Filial.builder()
                .nome("Filial Teste")
                .cidade("Feira de Santana")
                .estado("BA")
                .build());
    }

    // ========================================================================
    // LOGIN
    // ========================================================================

    @Test
    @DisplayName("Login com credenciais corretas retorna 200 com tokens")
    void login_comCredenciaisCorretas_retornaTokens() {
        criarUsuario("motorista@test.com", Papel.MOTORISTA, false);

        var request = Map.of("email", "motorista@test.com", "senha", "senha123");
        var response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("accessToken", "refreshToken", "papel");
        assertThat(response.getBody().get("papel")).isEqualTo("MOTORISTA");
    }

    @Test
    @DisplayName("Login com senha errada retorna 401 com mensagem genérica")
    void login_comSenhaErrada_retorna401() {
        criarUsuario("motorista@test.com", Papel.MOTORISTA, false);

        var request = Map.of("email", "motorista@test.com", "senha", "senhaErrada");
        var response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Mensagem genérica — não revela qual campo está errado
        assertThat(response.getBody().get("detail").toString())
                .contains("Email ou senha incorretos");
    }

    @Test
    @DisplayName("Login com email inexistente retorna 401 (não revelar existência do email)")
    void login_comEmailInexistente_retorna401() {
        var request = Map.of("email", "naoexiste@test.com", "senha", "qualquer");
        var response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========================================================================
    // RBAC — CRITÉRIO PRINCIPAL DA FASE 1
    // ========================================================================

    @Test
    @DisplayName("MOTORISTA NÃO consegue acessar endpoint de admin — retorna 403")
    void motorista_naoAcessaEndpointAdmin_retorna403() {
        criarUsuario("motorista@test.com", Papel.MOTORISTA, false);
        String token = fazerLoginEObterToken("motorista@test.com");

        var response = restTemplate.exchange(
                "/api/v1/admin/ping",
                HttpMethod.GET,
                comToken(token),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("VENDEDOR NÃO consegue acessar endpoint de admin — retorna 403")
    void vendedor_naoAcessaEndpointAdmin_retorna403() {
        criarUsuario("vendedor@test.com", Papel.VENDEDOR, false);
        String token = fazerLoginEObterToken("vendedor@test.com");

        var response = restTemplate.exchange(
                "/api/v1/admin/ping",
                HttpMethod.GET,
                comToken(token),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN acessa endpoint de admin — retorna 200")
    void admin_acessaEndpointAdmin_retorna200() {
        criarUsuario("admin@test.com", Papel.ADMIN, false);
        String token = fazerLoginEObterToken("admin@test.com");

        var response = restTemplate.exchange(
                "/api/v1/admin/ping",
                HttpMethod.GET,
                comToken(token),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Request sem token retorna 401")
    void semToken_retorna401() {
        var response = restTemplate.getForEntity("/api/v1/admin/ping", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Request com token expirado/inválido retorna 401")
    void tokenInvalido_retorna401() {
        var response = restTemplate.exchange(
                "/api/v1/admin/ping",
                HttpMethod.GET,
                comToken("token.invalido.aqui"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========================================================================
    // REFRESH TOKEN
    // ========================================================================

    @Test
    @DisplayName("Refresh com token válido retorna novo access token")
    void refresh_comTokenValido_retornaNovoAccessToken() {
        criarUsuario("motorista@test.com", Papel.MOTORISTA, false);

        var loginResponse = fazerLoginCompleto("motorista@test.com");
        String refreshToken = loginResponse.get("refreshToken").toString();

        var refreshRequest = Map.of("refreshToken", refreshToken);
        var response = restTemplate.postForEntity(
                "/api/v1/auth/refresh", refreshRequest, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
        // Novo access token deve ser diferente do original
        assertThat(response.getBody().get("accessToken"))
                .isNotEqualTo(loginResponse.get("accessToken"));
    }

    @Test
    @DisplayName("Logout revoga o refresh token; refresh subsequente retorna 401")
    void logout_revogaRefreshToken_refreshSubsequenteRetorna401() {
        criarUsuario("motorista@test.com", Papel.MOTORISTA, false);

        var loginResponse = fazerLoginCompleto("motorista@test.com");
        String refreshToken = loginResponse.get("refreshToken").toString();

        // Logout
        var logoutRequest = Map.of("refreshToken", refreshToken);
        restTemplate.postForEntity("/api/v1/auth/logout", logoutRequest, Void.class);

        // Tentar refresh após logout → deve falhar
        var refreshRequest = Map.of("refreshToken", refreshToken);
        var response = restTemplate.postForEntity(
                "/api/v1/auth/refresh", refreshRequest, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========================================================================
    // MULTI-TENANT — isolamento de filial
    // ========================================================================

    @Test
    @DisplayName("Token contém filial_id da filial do usuário")
    void token_contemFilialIdDoUsuario() {
        criarUsuario("motorista@test.com", Papel.MOTORISTA, false);
        var loginResponse = fazerLoginCompleto("motorista@test.com");

        assertThat(loginResponse.get("filialId").toString())
                .isEqualTo(filial.getId().toString());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private void criarUsuario(String email, Papel papel, boolean adminGlobal) {
        usuarioRepository.save(Usuario.builder()
                .filial(filial)
                .nome("Usuário Teste")
                .email(email)
                .papel(papel)
                .adminGlobal(adminGlobal)
                .senhaHash(passwordEncoder.encode("senha123"))
                .ativo(true)
                .build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fazerLoginCompleto(String email) {
        var request = Map.of("email", email, "senha", "senha123");
        var response = restTemplate.postForEntity("/api/v1/auth/login", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String fazerLoginEObterToken(String email) {
        return fazerLoginCompleto(email).get("accessToken").toString();
    }

    private HttpEntity<Void> comToken(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
