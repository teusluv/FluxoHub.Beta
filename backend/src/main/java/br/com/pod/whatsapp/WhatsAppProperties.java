package br.com.pod.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pod.whatsapp")
public record WhatsAppProperties(
        String verifyToken,
        String appSecret,
        String phoneNumberId,
        String accessToken,
        int rateLimitRequestsPerMinute
) {}
