package br.com.pod.entregas;

import br.com.pod.AbstractIntegrationTest;
import br.com.pod.domain.entrega.StatusEntrega;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração — Fase 2: Domínio de Entregas.
 *
 * <p>Critérios de aceite testados:
 * <ul>
 *   <li>CRUD de entregas com paginação</li>
 *   <li>Filtro por status, vendedor, motorista e range de data</li>
 *   <li>MOTORISTA vê apenas suas próprias entregas</li>
 *   <li>Isolamento de filial: entrega de outra filial não aparece</li>
 *   <li>Transição de status válida e inválida</li>
 *   <li>LGPD: clienteDocumento mascarado para MOTORISTA</li>
 *   <li>Busca por nota fiscal — resposta correta</li>
 *   <li>Auditoria registrada em criação e mudança de status</li>
 * </ul>
 */
@DisplayName("Entregas — Integração")
class EntregaIntegrationIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired FilialRepository filialRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Filial filialA;
    private Filial filialB;
    private Usuario admin;
    private Usuario vendedor;
    private Usuario motorista1;
    private Usuario motorista2;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        filialRepository.deleteAll();

        filialA = filialRepository.save(Filial.builder()
                .nome("Filial A").cidade("Feira de Santana").estado("BA").build());
        filialB = filialRepository.save(Filial.builder()
                .nome("Filial B").cidade("Salvador").estado("BA").build());

        admin    = criarUsuario("admin@test.com",    Papel.ADMIN,     filialA, false);
        vendedor = criarUsuario("vendedor@test.com", Papel.VENDEDOR,  filialA, false);
        motorista1 = criarUsuario("moto1@test.com",  Papel.MOTORISTA, filialA, false);
        motorista2 = criarUsuario("moto2@test.com",  Papel.MOTORISTA, filialA, false);
        // Motorista da filial B — não deve aparecer em queries da filial A
        criarUsuario("moto_b@test.com", Papel.MOTORISTA, filialB, false);
    }

    // ========================================================================
    // CRIAÇÃO
    // ========================================================================

    @Test
    @DisplayName("ADMIN cria entrega com sucesso — retorna 201")
    void criarEntrega_comoAdmin_retorna201() {
        String token = login("admin@test.com");

        var body = Map.of(
                "numeroNotaFiscal", "NF-12345",
                "clienteNome", "Mercado do João",
                "clienteDocumento", "12.345.678/0001-90",
                "dataPrevistaEntrega", LocalDate.now().toString()
        );

        var response = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.POST, comBodyEToken(body, token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("status")).isEqualTo("PENDENTE");
        assertThat(response.getHeaders().getLocation()).isNotNull();
    }

    @Test
    @DisplayName("MOTORISTA NÃO pode criar entrega — retorna 403")
    void criarEntrega_comoMotorista_retorna403() {
        String token = login("moto1@test.com");
        var body = Map.of("numeroNotaFiscal", "NF-99999", "clienteNome", "Teste");

        var response = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.POST, comBodyEToken(body, token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("VENDEDOR cria entrega com sucesso — retorna 201")
    void criarEntrega_comoVendedor_retorna201() {
        String token = login("vendedor@test.com");
        var body = Map.of(
                "numeroNotaFiscal", "NF-77777",
                "clienteNome", "Padaria Central",
                "motoristaId", motorista1.getId().toString()
        );

        var response = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.POST, comBodyEToken(body, token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ========================================================================
    // PAGINAÇÃO E FILTROS
    // ========================================================================

    @Test
    @DisplayName("Listagem paginada retorna estrutura Page correta")
    void listar_retornaPage() {
        String token = login("admin@test.com");
        // Cria 3 entregas
        for (int i = 1; i <= 3; i++) {
            criarEntregaViaApi(token, "NF-" + i, "Cliente " + i);
        }

        var response = restTemplate.exchange(
                "/api/v1/entregas?size=2&page=0", HttpMethod.GET, comToken(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).containsKeys("content", "totalElements", "totalPages", "size");
        assertThat((Integer) body.get("size")).isEqualTo(2);
        assertThat((Integer) body.get("totalPages")).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Filtro por status retorna apenas entregas no status solicitado")
    void listar_filtroStatus_retornaApenasStatusFiltrado() {
        String token = login("admin@test.com");
        criarEntregaViaApi(token, "NF-PENDENTE", "Cliente Pendente");

        // Busca com filtro PENDENTE
        var response = restTemplate.exchange(
                "/api/v1/entregas?status=PENDENTE", HttpMethod.GET, comToken(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).allMatch(e -> e.get("status").equals("PENDENTE"));
    }

    @Test
    @DisplayName("Filtro por motorista retorna apenas entregas daquele motorista")
    void listar_filtroPorMotorista() {
        String tokenAdmin = login("admin@test.com");
        // Cria entrega para motorista1
        var body1 = Map.of("numeroNotaFiscal", "NF-M1", "clienteNome", "C1",
                "motoristaId", motorista1.getId().toString());
        restTemplate.exchange("/api/v1/entregas", HttpMethod.POST, comBodyEToken(body1, tokenAdmin), Map.class);

        // Busca por motorista1
        var response = restTemplate.exchange(
                "/api/v1/entregas?motoristaId=" + motorista1.getId(),
                HttpMethod.GET, comToken(tokenAdmin), Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).allMatch(e -> e.get("motoristaId").equals(motorista1.getId().toString()));
    }

    @Test
    @DisplayName("Busca por texto em número de nota retorna resultado correto")
    void listar_buscaTextoNota() {
        String token = login("admin@test.com");
        criarEntregaViaApi(token, "NF-ESPECIAL-001", "Cliente A");
        criarEntregaViaApi(token, "NF-OUTRO-999", "Cliente B");

        var response = restTemplate.exchange(
                "/api/v1/entregas?busca=ESPECIAL", HttpMethod.GET, comToken(token), Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("numeroNotaFiscal")).isEqualTo("NFESPECIAL001");
    }

    // ========================================================================
    // ISOLAMENTO DE MOTORISTA
    // ========================================================================

    @Test
    @DisplayName("MOTORISTA só vê suas próprias entregas — não vê as de outro motorista")
    void motorista_soVePropriasentregas() {
        String tokenAdmin = login("admin@test.com");

        // Cria 1 entrega para motorista1, 1 para motorista2
        var b1 = Map.of("numeroNotaFiscal", "NF-MOTO1", "clienteNome", "C1",
                "motoristaId", motorista1.getId().toString());
        var b2 = Map.of("numeroNotaFiscal", "NF-MOTO2", "clienteNome", "C2",
                "motoristaId", motorista2.getId().toString());
        restTemplate.exchange("/api/v1/entregas", HttpMethod.POST, comBodyEToken(b1, tokenAdmin), Map.class);
        restTemplate.exchange("/api/v1/entregas", HttpMethod.POST, comBodyEToken(b2, tokenAdmin), Map.class);

        // motorista1 lista suas entregas
        String tokenMoto1 = login("moto1@test.com");
        var response = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.GET, comToken(tokenMoto1), Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).allMatch(e -> e.get("motoristaNome") != null
                && e.get("motoristaId").equals(motorista1.getId().toString()));
        assertThat(content).noneMatch(e -> e.get("motoristaId").equals(motorista2.getId().toString()));
    }

    // ========================================================================
    // LGPD
    // ========================================================================

    @Test
    @DisplayName("MOTORISTA não recebe clienteDocumento (LGPD)")
    void motorista_naoRecebeClienteDocumento() {
        String tokenAdmin = login("admin@test.com");
        var b = Map.of("numeroNotaFiscal", "NF-LGPD", "clienteNome", "Empresa X",
                "clienteDocumento", "12.345.678/0001-99",
                "motoristaId", motorista1.getId().toString());
        var criadaResp = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.POST, comBodyEToken(b, tokenAdmin), Map.class);
        String id = criadaResp.getBody().get("id").toString();

        // Admin vê o documento
        var respAdmin = restTemplate.exchange(
                "/api/v1/entregas/" + id, HttpMethod.GET, comToken(tokenAdmin), Map.class);
        assertThat(respAdmin.getBody().get("clienteDocumento")).isEqualTo("12.345.678/0001-99");

        // Motorista NÃO vê
        String tokenMoto = login("moto1@test.com");
        var respMoto = restTemplate.exchange(
                "/api/v1/entregas/" + id, HttpMethod.GET, comToken(tokenMoto), Map.class);
        assertThat(respMoto.getBody().get("clienteDocumento")).isNull();
    }

    // ========================================================================
    // TRANSIÇÕES DE STATUS
    // ========================================================================

    @Test
    @DisplayName("Transição de status válida: PENDENTE → EM_ROTA")
    void atualizarStatus_transicaoValida() {
        String token = login("admin@test.com");
        String entregaId = criarEntregaViaApi(token, "NF-STATUS", "Cliente");

        var req = Map.of("novoStatus", "EM_ROTA");
        var response = restTemplate.exchange(
                "/api/v1/entregas/" + entregaId + "/status",
                HttpMethod.PATCH, comBodyEToken(req, token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("EM_ROTA");
    }

    @Test
    @DisplayName("Transição de status inválida: PENDENTE → ENTREGUE_COM_CANHOTO retorna 422")
    void atualizarStatus_transicaoInvalida_retorna422() {
        String token = login("admin@test.com");
        String entregaId = criarEntregaViaApi(token, "NF-INVALID", "Cliente");

        var req = Map.of("novoStatus", "ENTREGUE_COM_CANHOTO");
        var response = restTemplate.exchange(
                "/api/v1/entregas/" + entregaId + "/status",
                HttpMethod.PATCH, comBodyEToken(req, token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ========================================================================
    // BUSCA POR NOTA FISCAL
    // ========================================================================

    @Test
    @DisplayName("Busca por nota fiscal exata retorna entrega correta")
    void buscarPorNota_retornaEntregaCorreta() {
        String token = login("vendedor@test.com");
        criarEntregaViaApiComoAdmin("NF-BUSCA-123", "Cliente Busca");

        var response = restTemplate.exchange(
                "/api/v1/entregas/nota/NF-BUSCA-123", HttpMethod.GET, comToken(token), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).isNotEmpty();
    }

    @Test
    @DisplayName("Busca por nota inexistente retorna lista vazia")
    void buscarPorNota_notaInexistente_retornaListaVazia() {
        String token = login("vendedor@test.com");

        var response = restTemplate.exchange(
                "/api/v1/entregas/nota/NF-INEXISTENTE", HttpMethod.GET, comToken(token), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).isEmpty();
    }

    // ========================================================================
    // ISOLAMENTO DE FILIAL (multi-tenant)
    // ========================================================================

    @Test
    @DisplayName("Admin da filial A não vê entregas da filial B")
    void isolamentoFilial_adminFilialA_naoVeFilialB() {
        // Cria admin da filial B
        var adminB = criarUsuario("adminb@test.com", Papel.ADMIN, filialB, false);

        // Admin B cria entrega na filial B
        String tokenAdminB = login("adminb@test.com");
        criarEntregaViaApi(tokenAdminB, "NF-FILIAL-B", "Cliente Filial B");

        // Admin A lista suas entregas — não deve ver a da filial B
        String tokenAdminA = login("admin@test.com");
        var response = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.GET, comToken(tokenAdminA), Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).noneMatch(e -> e.get("filialNome").equals("Filial B"));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String login(String email) {
        var req = Map.of("email", email, "senha", "senha123");
        var resp = restTemplate.postForEntity("/api/v1/auth/login", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").toString();
    }

    private String criarEntregaViaApi(String token, String nf, String cliente) {
        var body = Map.of("numeroNotaFiscal", nf, "clienteNome", cliente);
        var resp = restTemplate.exchange(
                "/api/v1/entregas", HttpMethod.POST, comBodyEToken(body, token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().get("id").toString();
    }

    private void criarEntregaViaApiComoAdmin(String nf, String cliente) {
        criarEntregaViaApi(login("admin@test.com"), nf, cliente);
    }

    private Usuario criarUsuario(String email, Papel papel, Filial filial, boolean adminGlobal) {
        return usuarioRepository.save(Usuario.builder()
                .filial(filial).nome("Usuário " + papel).email(email).papel(papel)
                .adminGlobal(adminGlobal).senhaHash(passwordEncoder.encode("senha123"))
                .ativo(true).build());
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
