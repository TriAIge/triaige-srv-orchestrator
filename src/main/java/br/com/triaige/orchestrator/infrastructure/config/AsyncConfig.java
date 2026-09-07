package br.com.triaige.orchestrator.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Fase 4, spec seção 2.2: disparo da análise de IA acontece em thread separada, após o
 * commit da transação do callback {@code mcp-result}, para não segurar a resposta HTTP ao
 * MCP pela duração de uma chamada que pode levar até {@code ANALYSIS_TIMEOUT_MS} (120s).
 * Sizing modesto: cada tarefa é I/O-bound (HTTP + S3 + SQS) e o volume esperado é baixo
 * (uma análise por sessão finalizada).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * {@code TriggerAiAnalysisUseCase} orquestra várias transações curtas separadas por I/O
     * externo (HTTP, S3, SQS) dentro de um único método não-transacional — {@code @Transactional}
     * não funcionaria ali por auto-invocação (chamada {@code this.metodo()} dentro da mesma
     * classe não passa pelo proxy AOP do Spring). {@link TransactionTemplate} programático
     * evita esse problema por completo.
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
