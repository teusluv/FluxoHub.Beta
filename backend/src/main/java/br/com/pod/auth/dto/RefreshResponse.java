package br.com.pod.auth.dto;

/** Resposta ao refresh: apenas o novo access token. O refresh token permanece o mesmo. */
public record RefreshResponse(
        String accessToken,
        long accessExpiryMs
) {}
