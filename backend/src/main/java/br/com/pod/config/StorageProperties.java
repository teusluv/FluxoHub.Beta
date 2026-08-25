package br.com.pod.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de armazenamento de objetos (MinIO/S3) lidas do application.yml.
 */
@ConfigurationProperties(prefix = "pod.storage")
public record StorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        int presignedUrlExpiryMinutes
) {}
