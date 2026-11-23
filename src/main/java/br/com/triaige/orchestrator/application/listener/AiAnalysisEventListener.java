package br.com.triaige.orchestrator.application.listener;

import br.com.triaige.orchestrator.application.usecase.TriggerAiAnalysisUseCase;
import br.com.triaige.orchestrator.domain.event.AiAnalysisTriggeredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispara a análise em outra thread só depois do commit do callback, para que ela leia o status
 * já gravado. A lógica fica em {@link TriggerAiAnalysisUseCase}.
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
