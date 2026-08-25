package br.com.pod.domain.canhoto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request de upload de um único canhoto (multipart/form-data).
 *
 * <p>O campo {@code capturedAt} é o timestamp do device — preserva o horário
 * real da entrega mesmo quando o motorista está offline horas antes de sincronizar.
 */
public record UploadCanhotoRequest(
        @NotNull UUID entregaId,
        @NotBlank String deviceId,
        @NotNull OffsetDateTime capturedAt,
        @NotNull MultipartFile imagem
) {}
