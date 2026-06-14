package br.com.triaige.orchestrator.infrastructure.s3;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DocumentStoragePort {

    /**
     * Faz o upload do arquivo para o bucket de documentos crus, sob o prefixo
     * law-firm/{lawFirmId}/sessions/{sessionId}/raw/.
     *
     * @throws br.com.triaige.orchestrator.domain.exception.DocumentStorageException se o upload falhar
     */
    StoredDocument upload(UUID lawFirmId, UUID sessionId, MultipartFile file);

    /**
     * Grava o resultado da análise de IA (JSON) no bucket curated, sob o prefixo
     * law-firm/{lawFirmId}/sessions/{sessionId}/curated/.
     *
     * @throws br.com.triaige.orchestrator.domain.exception.DocumentStorageException se a gravação falhar
     */
    StoredDocument storeResult(UUID lawFirmId, UUID sessionId, String jsonContent);
}
