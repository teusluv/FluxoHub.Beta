package br.com.pod.security;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de Segurança — Isolamento Multi-Tenant (Seção 5 do Plano de QA).
 *
 * <p><strong>Prioridade máxima:</strong> uma falha aqui significa vazamento de
 * dados entre empresas clientes. Todos os testes têm status explícito PASSOU/FALHOU.
 *
 * <p>Cenários cobertos:
 * <ol>
 *   <li>Admin da filial A não lista entregas da filial B</li>
 *   <li>Admin da filial A não acessa entrega da filial B por ID (deve retornar 404)</li>
 *   <li>Lista de motoristas não vaza motoristas de outra filial</li>
 *   <li>Token adulterado manualmente é rejeitado com 401</li>
 *   <li>Busca por NF da filial B a partir da filial A retorna lista vazia</li>
 * </ol>
 */
@DisplayName("Segurança Multi-Tenant — Isolamento de Filial")
class MultiTenantSecurityIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired FilialRepository filialRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Filial filialA;
    private Filial filialB;
    private Usuario adminA;
    private Usuario adminB;
    private Usuario motoristaB;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        filialRepository.deleteAll();

        filialA = filialRepository.save(Filial.builder()
                .nome("Filial Alpha").cidade("Feira de Santana").estado("BA").build());
        filialB = filialRepository.save(Filial.builder()
                .nome("Filial Beta").cidade("Salvador").estado("BA").build());

        adminA    = criarUsuario("adminA@test.com",  Papel.ADMIN,     filialA);
        adminB    = criarUsuario("adminB@test.com",  Papel.ADMIN,     filialB);
        motoristaB = criarUsuario("motob@test.com",  Papel.MOTORISTA, filialB);
    }

    // =========================================================================
    // TESTE 1 — Listagem não vaza dados entre filiais
    // =========================================================================

    @Test
    @DisplayName("CRÍTICO: Admin filial A NÃO vê entregas da filial B — isolamento multi-tenant")
    void critico_adminFilialA_naoVeEntregasFilialB() {
        // Arrange: adminB cria uma entrega exclusiva da filial B
        String tokenAdminB = login("adminB@test.com");
        criarEntregaViaApi(tokenAdminB, "NF-SECRETA-FILIAL-B", "Cliente Filial Beta");

        // Act: adminA lista suas entregas
        String tokenAdminA = login("adminA@test.com");
        var response = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.GET, comToken(tokenAdminA), Map.class);

        // Assert: resposta OK, mas sem nenhum dado da filial B
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).noneMatch(e -> "Filial Beta".equals(e.get("filialNome")));
        // NF sanitizada pelo service: NFRETAFILIALB não aparece
        assertThat(content).noneMatch(e ->
                e.get("numeroNotaFiscal") != null &&
                e.get("numeroNotaFiscal").toString().contains("SECRETAFILIALB"));
    }

    // =========================================================================
    // TESTE 2 — Acesso por ID direto deve ser bloqueado pelo tenant
    // =========================================================================

    @Test
    @DisplayName("CRÍTICO: Admin filial A NÃO consegue GET de entrega da filial B por ID direto")
    void critico_adminFilialA_naoAcessaEntregaFilialBPorId() {
        // Arrange: adminB cria entrega e obtém o ID real do DB
        String tokenAdminB = login("adminB@test.com");
        String entregaFilialBId = criarEntregaViaApi(tokenAdminB, "NF-ID-DIRETO-B", "Cliente B");

        // Act: adminA tenta GET direto com o ID da entrega da filial B
        String tokenAdminA = login("adminA@test.com");
        var response = restTemplate.exchange(
                "/api/v1/entregas/" + entregaFilialBId,
                HttpMethod.GET, comToken(tokenAdminA), Map.class);

        // Assert: deve retornar 404 (o tenant filter esconde o registro)
        // Uma falha aqui significa vazamento de dados — CRÍTICO
        assertThat(response.getStatusCode())
                .as("Entrega da filial B não deve ser encontrada pelo admin da filial A — isolamento de tenant falhou")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // TESTE 3 — Lista de motoristas não vaza entre filiais
    // =========================================================================

    @Test
    @DisplayName("CRÍTICO: Lista de motoristas não vaza motoristas de outra filial")
    void critico_listaMotoristasNaoVazaOutraFilial() {
        // Act: adminA busca motoristas da sua filial
        String tokenAdminA = login("adminA@test.com");
        var response = restTemplate.exchange(
                "/api/v1/usuarios/motoristas", HttpMethod.GET, comToken(tokenAdminA), List.class);

        // Assert: motoristaB (da filial B) NÃO aparece na lista
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> motoristas = (List<Map<String, Object>>) response.getBody();
        assertThat(motoristas).noneMatch(m -> "motob@test.com".equals(m.get("email")));
        assertThat(motoristas).noneMatch(m ->
                m.get("id") != null && m.get("id").toString().equals(motoristaB.getId().toString()));
    }

    // =========================================================================
    // TESTE 4 — Token adulterado é rejeitado por assinatura inválida
    // =========================================================================

    @Test
    @DisplayName("CRÍTICO: Token JWT adulterado manualmente é rejeitado com 401")
    void critico_tokenAdulterado_rejeitadoComm401() {
        // Arrange: obter token legítimo
        String tokenOriginal = login("adminA@test.com");

        // Adulterar o token: JWT tem 3 segmentos separados por "."
        // Alterar um caractere no payload (segmento do meio) invalida a assinatura HMAC
        String[] partes = tokenOriginal.split("\\.");
        assertThat(partes).hasSize(3); // confirma que é um JWT real

        // Troca o último caractere do payload por um diferente
        String payloadOriginal = partes[1];
        char ultimoChar = payloadOriginal.charAt(payloadOriginal.length() - 1);
        char charSubstituto = (ultimoChar == 'A') ? 'B' : 'A';
        String payloadAdulterado = payloadOriginal.substring(0, payloadOriginal.length() - 1) + charSubstituto;

        String tokenAdulterado = partes[0] + "." + payloadAdulterado + "." + partes[2];

        // Act: request com token adulterado
        var response = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.GET, comToken(tokenAdulterado), Map.class);

        // Assert: DEVE ser rejeitado — o backend valida a assinatura HMAC
        // Se retornar 200, a assinatura não está sendo validada — FALHA CRÍTICA DE SEGURANÇA
        assertThat(response.getStatusCode())
                .as("Token com payload adulterado deve ser rejeitado — assinatura HMAC inválida")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // =========================================================================
    // TESTE 5 — Busca por NF cruzada entre filiais retorna vazio
    // =========================================================================

    @Test
    @DisplayName("Busca por NF da filial B por admin da filial A retorna lista vazia")
    void buscaPorNF_outraFilial_retornaListaVazia() {
        // Arrange: adminB cria entrega com NF específica
        String tokenAdminB = login("adminB@test.com");
        criarEntregaViaApi(tokenAdminB, "NF-CROSS-TENANT-TEST", "Cliente Cross");

        // Act: adminA tenta buscar esta NF (deve ser invisível)
        String tokenAdminA = login("adminA@test.com");
        var response = restTemplate.exchange(
                "/api/v1/entregas/nota/NF-CROSS-TENANT-TEST",
                HttpMethod.GET, comToken(tokenAdminA), List.class);

        // Assert: 200 com lista vazia — o tenant filter bloqueia o resultado
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).isEmpty();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String login(String email) {
        var req = Map.of("email", email, "senha", "senha123");
        var resp = restTemplate.postForEntity("/api/v1/auth/login", req, Map.class);
        assertThat(resp.getStatusCode())
                .as("Login como %s falhou — verifique o setUp", email)
                .isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").toString();
    }

    @SuppressWarnings("unchecked")
    private String criarEntregaViaApi(String token, String nf, String cliente) {
        var body = Map.of("numeroNotaFiscal", nf, "clienteNome", cliente);
        var resp = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.POST, comBodyEToken(body, token), Map.class);
        assertThat(resp.getStatusCode())
                .as("Criação de entrega NF=%s falhou", nf)
                .isEqualTo(HttpStatus.CREATED);
        return resp.getBody().get("id").toString();
    }

    private Usuario criarUsuario(String email, Papel papel, Filial filial) {
        return usuarioRepository.save(Usuario.builder()
                .filial(filial)
                .nome("Usuário " + papel.name())
                .email(email)
                .papel(papel)
                .adminGlobal(false)
                .senhaHash(passwordEncoder.encode("senha123"))
                .ativo(true)
                .build());
    }

    private HttpEntity<Void> comToken(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<?, ?>> comBodyEToken(Map<?, ?> body, String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
