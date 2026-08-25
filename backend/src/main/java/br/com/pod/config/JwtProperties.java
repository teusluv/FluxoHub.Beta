package br.com.pod.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades JWT lidas do application.yml (prefixo: pod.jwt).
 * Usar @ConfigurationProperties em vez de @Value garante que todos os parâmetros
 * JWT estejam documentados e validados em um único lugar.
 */
@ConfigurationProperties(prefix = "pod.jwt")
public record JwtProperties(
        String secret,
        long accessExpiryMs,
        long refreshExpiryMs
) {}
