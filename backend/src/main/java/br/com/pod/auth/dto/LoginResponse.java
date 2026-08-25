package br.com.pod.auth.dto;

import br.com.pod.domain.usuario.Papel;

import java.util.UUID;

/**
 * Resposta do login contendo os dois tokens e dados básicos do usuário.
 *
 * <p>O access token vai no body (não em cookie) para compatibilidade com
 * o app mobile. O refresh token deve ser armazenado de forma segura
 * no device (ex: SecureStore do Expo, não AsyncStorage).
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long accessExpiryMs,
        UUID usuarioId,
        String nome,
        String email,
        Papel papel,
        UUID filialId,
        String filialNome,
        boolean adminGlobal
) {}
