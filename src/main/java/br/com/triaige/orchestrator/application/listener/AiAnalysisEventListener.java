package br.com.triaige.orchestrator.application.listener;

import br.com.triaige.orchestrator.application.usecase.TriggerAiAnalysisUseCase;
import br.com.triaige.orchestrator.domain.event.AiAnalysisTriggeredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispara a análise de IA em thread separada, só depois que
 * {@code triage_sessions.status} pós-MCP já está commitado — garante que a thread de
 * análise, ao (eventualmente) reler a sessão, veja o estado correto. Fino de propósito:
 * toda a lógica fica em {@link TriggerAiAnalysisUseCase}, testável sem contexto Spring.
 */
@Component
@RequiredArgsConstructor
public class AiAnalysisEventListener {

    private final TriggerAiAnalysisUseCase triggerAiAnalysisUseCase;

    @Async("analysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAiAnalysisTriggered(AiAnalysisTriggeredEvent event) {
        triggerAiAnalysisUseCase.execute(event);
    }
}
