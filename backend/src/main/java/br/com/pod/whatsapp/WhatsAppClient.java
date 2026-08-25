package br.com.pod.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);
    private final RestClient restClient;
    private final WhatsAppProperties props;

    public WhatsAppClient(WhatsAppProperties props, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.restClient = restClientBuilder
                .baseUrl("https://graph.facebook.com/v19.0/" + props.phoneNumberId())
                .defaultHeader("Authorization", "Bearer " + props.accessToken())
                .build();
    }

    public void enviarTexto(String to, String text) {
        try {
            restClient.post()
                    .uri("/messages")
                    .body(Map.of(
                            "messaging_product", "whatsapp",
                            "recipient_type", "individual",
                            "to", to,
                            "type", "text",
                            "text", Map.of("preview_url", false, "body", text)
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Mensagem de texto enviada para {}", to);
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem via WhatsApp API", e);
        }
    }

    public void enviarImagem(String to, String imageUrl, String caption) {
        try {
            restClient.post()
                    .uri("/messages")
                    .body(Map.of(
                            "messaging_product", "whatsapp",
                            "recipient_type", "individual",
                            "to", to,
                            "type", "image",
                            "image", Map.of("link", imageUrl, "caption", caption)
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Imagem enviada para {}", to);
        } catch (Exception e) {
            log.error("Erro ao enviar imagem via WhatsApp API", e);
        }
    }
}
