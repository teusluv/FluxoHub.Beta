package br.com.pod.whatsapp;

import br.com.pod.whatsapp.dto.WebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private final WhatsAppProperties props;
    private final WhatsAppService whatsAppService;

    public WhatsAppWebhookController(WhatsAppProperties props, WhatsAppService whatsAppService) {
        this.props = props;
        this.whatsAppService = whatsAppService;
    }

    /**
     * Endpoint de verificação (necessário para registrar o Webhook no painel da Meta).
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && props.verifyToken().equals(token)) {
            log.info("Webhook verificado com sucesso!");
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Endpoint que recebe as mensagens.
     */
    @PostMapping
    public ResponseEntity<Void> receiveMessage(@RequestBody WebhookPayload payload) {
        // O WhatsApp exige que retornemos 200 OK imediatamente.
        // O processamento deve ser assíncrono para evitar timeouts.
        try {
            whatsAppService.processarPayloadAsync(payload);
        } catch (Exception e) {
            log.error("Erro ao enfileirar payload do webhook", e);
        }
        return ResponseEntity.ok().build();
    }
}
