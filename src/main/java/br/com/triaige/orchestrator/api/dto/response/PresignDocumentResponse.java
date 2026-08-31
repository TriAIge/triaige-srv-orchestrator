package br.com.triaige.orchestrator.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignDocumentResponse {

    private UUID documentId;
    private UUID attachmentGroupId;
    private Integer partNumber;
    private String uploadUrl;
    private LocalDateTime uploadUrlExpiresAt;
    private String rawBucket;
    private String rawObjectKey;
}
