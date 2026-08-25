package br.com.pod.whatsapp;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, AtomicInteger> requestsPorTelefone = new ConcurrentHashMap<>();
    private final WhatsAppProperties props;

    public RateLimiterService(WhatsAppProperties props) {
        this.props = props;
    }

    /**
     * Tenta consumir 1 token para o telefone. 
     * Retorna true se permitido, false se excedeu o limite.
     */
    public boolean permitir(String telefone) {
        if (props.rateLimitRequestsPerMinute() <= 0) {
            return true; // Rate limit desativado se configurado como <= 0
        }
        
        var count = requestsPorTelefone.computeIfAbsent(telefone, k -> new AtomicInteger(0));
        return count.incrementAndGet() <= props.rateLimitRequestsPerMinute();
    }

    /**
     * Reseta os contadores a cada minuto (60.000 ms).
     */
    @Scheduled(fixedRate = 60000)
    public void reset() {
        requestsPorTelefone.clear();
    }
}
