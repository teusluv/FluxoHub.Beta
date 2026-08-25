package br.com.pod.canhotos;

import br.com.pod.config.StorageProperties;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Serviço de armazenamento de objetos — abstrai MinIO/S3.
 *
 * <p><strong>Segurança:</strong> o bucket é privado. Nenhuma URL pública é gerada.
 * Imagens são acessadas exclusivamente via URLs pré-assinadas com expiração de
 * {@code presignedUrlExpiryMinutes} (padrão: 15min).
 *
 * <p><strong>Estrutura de chave no bucket:</strong>
 * {@code canhotos/{ano}/{mes}/{uuid}.jpg}
 * Permite navegação temporal e rotação de políticas de ciclo de vida (ex: Glacier após 1 ano).
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final MinioClient minioClient;
    private final StorageProperties props;

    public StorageService(MinioClient minioClient, StorageProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    /**
     * Faz upload de uma imagem e retorna o caminho relativo no bucket.
     *
     * @param arquivo arquivo multipart enviado pelo mobile
     * @return caminho relativo (ex: {@code canhotos/2024/08/abc123.jpg})
     * @throws StorageException se o upload falhar
     */
    public String upload(MultipartFile arquivo) {
        String chave = gerarChave(arquivo.getOriginalFilename());
        try (InputStream is = arquivo.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(chave)
                    .stream(is, arquivo.getSize(), -1)
                    .contentType(detectarContentType(arquivo.getContentType()))
                    .build()
            );
            log.info("Upload concluído: bucket={} chave={} bytes={}", props.bucket(), chave, arquivo.getSize());
            return chave;
        } catch (Exception e) {
            log.error("Falha no upload para MinIO: chave={}", chave, e);
            throw new StorageException("Falha ao armazenar imagem do canhoto", e);
        }
    }

    /**
     * Gera uma URL pré-assinada para acesso temporário à imagem.
     *
     * <p>A URL expira em {@link StorageProperties#presignedUrlExpiryMinutes()} minutos.
     * Nunca retornar URL pública — o bucket é privado.
     *
     * @param caminhoRelativo caminho retornado pelo {@link #upload}
     * @return URL pré-assinada com tempo de vida limitado
     */
    public String gerarUrlPresignada(String caminhoRelativo) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(props.bucket())
                    .object(caminhoRelativo)
                    .expiry(props.presignedUrlExpiryMinutes(), TimeUnit.MINUTES)
                    .build()
            );
        } catch (Exception e) {
            log.error("Falha ao gerar URL pré-assinada: objeto={}", caminhoRelativo, e);
            throw new StorageException("Falha ao gerar URL de acesso à imagem", e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    /**
     * Gera chave de objeto com estrutura temporal para facilitar lifecycle policies.
     * Formato: {@code canhotos/{ano}/{mes}/{uuid}.{ext}}
     */
    private String gerarChave(String nomeOriginal) {
        LocalDate hoje = LocalDate.now();
        String ext = extrairExtensao(nomeOriginal);
        return "canhotos/%d/%02d/%s%s".formatted(
            hoje.getYear(),
            hoje.getMonthValue(),
            UUID.randomUUID(),
            ext
        );
    }

    private String extrairExtensao(String nome) {
        if (nome == null || !nome.contains(".")) return ".jpg";
        return nome.substring(nome.lastIndexOf('.'));
    }

    private String detectarContentType(String contentType) {
        if (contentType != null && contentType.startsWith("image/")) return contentType;
        return "image/jpeg"; // fallback seguro
    }

    /** Exceção de infraestrutura de storage (não expor ao usuário — handler mapeia para 500). */
    public static class StorageException extends RuntimeException {
        public StorageException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
