package br.com.pod.whatsapp;

import br.com.pod.canhotos.StorageService;
import br.com.pod.domain.canhoto.Canhoto;
import br.com.pod.domain.canhoto.CanhotoRepository;
import br.com.pod.domain.entrega.EntregaResponse;
import br.com.pod.entregas.EntregaService;
import br.com.pod.whatsapp.dto.WebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final EntregaService entregaService;
    private final CanhotoRepository canhotoRepository;
    private final StorageService storageService;
    private final WhatsAppClient whatsAppClient;
    private final RateLimiterService rateLimiterService;

    public WhatsAppService(
            EntregaService entregaService,
            CanhotoRepository canhotoRepository,
            StorageService storageService,
            WhatsAppClient whatsAppClient,
            RateLimiterService rateLimiterService) {
        this.entregaService = entregaService;
        this.canhotoRepository = canhotoRepository;
        this.storageService = storageService;
        this.whatsAppClient = whatsAppClient;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Processa a mensagem de forma assíncrona para não travar o webhook da Meta.
     */
    @Async
    public void processarPayloadAsync(WebhookPayload payload) {
        if (payload == null || payload.entry() == null) return;

        for (var entry : payload.entry()) {
            if (entry.changes() == null) continue;
            
            for (var change : entry.changes()) {
                var value = change.value();
                if (value == null || value.messages() == null) continue;
                
                for (var message : value.messages()) {
                    if ("text".equals(message.type()) && message.text() != null) {
                        processarMensagemTexto(message.from(), message.text().body());
                    }
                }
            }
        }
    }

    private void processarMensagemTexto(String fromPhone, String text) {
        log.info("Recebida mensagem do número: {}", fromPhone);

        // 1. Rate Limiting por telefone
        if (!rateLimiterService.permitir(fromPhone)) {
            log.warn("Rate limit excedido para o número {}", fromPhone);
            return;
        }

        String comando = text.trim().toLowerCase();
        
        // 2. Verifica formato do comando "canhoto XXXXX"
        if (!comando.startsWith("canhoto ")) {
            whatsAppClient.enviarTexto(fromPhone, "Comando inválido. Use: *canhoto <número da nota>*");
            return;
        }

        String numeroNota = comando.substring("canhoto ".length()).trim();
        if (numeroNota.isEmpty()) {
            whatsAppClient.enviarTexto(fromPhone, "Por favor, informe o número da nota fiscal.");
            return;
        }

        // 3. Busca a Entrega (usa o índice GIN ultra-rápido)
        List<EntregaResponse> entregas = entregaService.buscarPorNota(numeroNota);
        if (entregas.isEmpty()) {
            whatsAppClient.enviarTexto(fromPhone, "Nenhum registro encontrado para a NF: " + numeroNota);
            return;
        }

        // Pega a entrega mais recente caso tenha múltiplas
        EntregaResponse entrega = entregas.get(0);

        // 4. Busca o canhoto associado à entrega
        List<Canhoto> canhotos = canhotoRepository.findByEntregaIdAndValidoTrueOrderByCapturadoEmDesc(entrega.id());
        
        if (canhotos.isEmpty()) {
            whatsAppClient.enviarTexto(fromPhone, 
                "Entrega encontrada (Status: " + entrega.status().name().replace("_", " ") + 
                "), mas o canhoto ainda não foi enviado pelo motorista.");
            return;
        }

        Canhoto canhoto = canhotos.get(0);

        // 5. Gera a URL pré-assinada
        String urlPresignada = storageService.gerarUrlPresignada(canhoto.getUrlImagem());

        // 6. Responde com a imagem
        String legenda = String.format("Canhoto da NF: *%s*\nCliente: %s", 
                entrega.numeroNotaFiscal(), 
                entrega.clienteNome());
                
        whatsAppClient.enviarImagem(fromPhone, urlPresignada, legenda);
    }
}
