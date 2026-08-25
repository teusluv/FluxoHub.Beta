package br.com.pod.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Refresh token é obrigatório")
        String refreshToken
) {}
