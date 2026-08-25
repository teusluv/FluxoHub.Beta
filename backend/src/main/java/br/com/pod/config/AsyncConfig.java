package br.com.pod.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuração do pool de threads para operações assíncronas (@Async).
 *
 * <p>Usado principalmente para processamento OCR após upload de imagens —
 * o endpoint de upload retorna imediatamente enquanto o OCR roda em background.
 *
 * <p>Dimensionamento conservador para início:
 * <ul>
 *   <li>corePoolSize = 2: processamento contínuo de uploads</li>
 *   <li>maxPoolSize = 10: pico de sincronizações simultâneas (vários motoristas)</li>
 *   <li>queueCapacity = 50: fila para absorver bursts sem rejeitar tasks</li>
 * </ul>
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "ocrTaskExecutor")
    public Executor ocrTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ocr-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
