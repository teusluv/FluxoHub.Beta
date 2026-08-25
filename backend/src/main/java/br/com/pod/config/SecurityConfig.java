package br.com.pod.config;

import br.com.pod.auth.filter.FilialContextFilter;
import br.com.pod.auth.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Configuração central de segurança do Spring Security 6.
 *
 * <p><strong>Hierarquia de autorização:</strong>
 * <ul>
 *   <li>Endpoints públicos: {@code /api/v1/auth/**}, actuator health, swagger</li>
 *   <li>{@code ADMIN}: {@code /api/v1/admin/**} e todos os outros</li>
 *   <li>{@code VENDEDOR}: {@code /api/v1/vendedor/**} e consultas</li>
 *   <li>{@code MOTORISTA}: apenas captura de canhotos e suas entregas</li>
 * </ul>
 *
 * <p>Usamos {@code hasAuthority} (não {@code hasRole}) porque as authorities
 * já são os nomes do enum sem prefixo ROLE_.
 *
 * <p>@EnableMethodSecurity habilita {@code @PreAuthorize} nos controllers
 * para controle de acesso fino (ex: motorista só vê suas próprias entregas).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final FilialContextFilter filialContextFilter;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          FilialContextFilter filialContextFilter,
                          UserDetailsService userDetailsService,
                          ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.filialContextFilter = filialContextFilter;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)         // API stateless não precisa de CSRF
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/whatsapp/webhook/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                         "/api-docs/**", "/api-docs.yaml").permitAll()

                        // Endpoints de admin — somente ADMIN
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ADMIN")

                        // Endpoints de vendedor e entregas
                        .requestMatchers(HttpMethod.POST, "/api/v1/entregas/*/notas")
                                .hasAnyAuthority("VENDEDOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/entregas/*/notas")
                                .hasAnyAuthority("VENDEDOR", "ADMIN", "MOTORISTA")
                        .requestMatchers(HttpMethod.POST, "/api/v1/entregas")
                                .hasAnyAuthority("VENDEDOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/entregas/**")
                                .hasAnyAuthority("VENDEDOR", "ADMIN", "MOTORISTA")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/entregas/*/status")
                                .hasAnyAuthority("MOTORISTA", "VENDEDOR", "ADMIN")
                        .requestMatchers("/api/v1/vendedor/**").hasAnyAuthority("VENDEDOR", "ADMIN")

                        // Canhotos: motorista pode fazer upload; vendedor/admin podem ler
                        .requestMatchers(HttpMethod.POST, "/api/v1/canhotos/**")
                                .hasAnyAuthority("MOTORISTA", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/canhotos/**")
                                .hasAnyAuthority("VENDEDOR", "ADMIN", "MOTORISTA")

                        // Qualquer outra request autenticada
                        .anyRequest().authenticated()
                )
                // JWT filter antes do UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // FilialContextFilter após JWT (precisa do SecurityContext populado)
                .addFilterAfter(filialContextFilter, JwtAuthenticationFilter.class)
                // Handler de autenticação negada (401) — resposta JSON padronizada
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            var pd = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED,
                                    "Token ausente, inválido ou expirado. Faça login novamente.");
                            pd.setType(URI.create("https://pod.com.br/errors/401"));
                            pd.setProperty("timestamp", Instant.now().toString());
                            objectMapper.writeValue(response.getOutputStream(), pd);
                        })
                        // Handler de acesso negado (403) — resposta JSON padronizada
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            var pd = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.FORBIDDEN,
                                    "Você não tem permissão para acessar este recurso.");
                            pd.setType(URI.create("https://pod.com.br/errors/403"));
                            pd.setProperty("timestamp", Instant.now().toString());
                            objectMapper.writeValue(response.getOutputStream(), pd);
                        })
                )
                .authenticationProvider(authenticationProvider())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt com strength 12 — bom equilíbrio entre segurança e performance
        // (~300ms por hash em hardware moderno, tornando brute force inviável)
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS: em produção, restringir {@code allowedOrigins} ao domínio real da aplicação.
     * Em dev, permite localhost nas portas padrão.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",   // web-admin dev
                "http://localhost:8081"    // Expo dev
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
