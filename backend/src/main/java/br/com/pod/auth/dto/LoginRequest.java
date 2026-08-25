package br.com.pod.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request de login com email e senha.
 * Usando record do Java 21 para DTOs imutáveis com validação declarativa.
 */
public record LoginRequest(
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        String senha
) {}
