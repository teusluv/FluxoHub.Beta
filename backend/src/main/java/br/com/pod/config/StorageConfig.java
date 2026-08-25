package br.com.pod.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do cliente MinIO/S3.
 *
 * <p>O {@link MinioClient} é thread-safe e reutilizável — criado como singleton Spring.
 * Propriedades lidas de {@link StorageProperties} (prefixo {@code pod.storage}).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public MinioClient minioClient(StorageProperties props) {
        return MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .build();
    }
}
