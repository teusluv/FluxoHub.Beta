package br.com.pod;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Classe base para todos os testes de integração.
 *
 * <p>Sobe um container PostgreSQL real via Testcontainers e configura
 * o datasource do Spring Boot dinamicamente. O Flyway roda automaticamente
 * ao iniciar o contexto — garantindo que os testes reflitam o schema real.
 *
 * <p>O container é compartilhado entre todos os testes da mesma JVM
 * (static) para performance — evitar subir/derrubar Postgres a cada teste.
 *
 * <p>Profile "test": desabilita o {@link br.com.pod.bootstrap.DataInitializer}
 * (cada teste cria seus próprios fixtures).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("pod_test")
            .withUsername("pod_test")
            .withPassword("pod_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        // JWT secret válido para testes (base64 de 256 bits)
        registry.add("pod.jwt.secret",
                () -> "dGVzdFNlY3JldEtleUZvclBvZFN5c3RlbUludGVncmF0aW9uVGVzdHMxMjM0NTY3ODk=");
        // MinIO: não usado em testes de auth — configuração fake
        registry.add("pod.storage.endpoint", () -> "http://localhost:9000");
        registry.add("pod.storage.access-key", () -> "test");
        registry.add("pod.storage.secret-key", () -> "test");
        registry.add("pod.storage.bucket", () -> "test");
    }
}
