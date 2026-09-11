package br.com.triaige.orchestrator.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    private String region = "us-east-1";
    private Sqs sqs = new Sqs();
    private S3 s3 = new S3();

    @Data
    public static class Sqs {
        private String endpoint;
        private String docsReceivedQueueUrl;
        private String docsPreprocessingQueueUrl;
        /** Fila Q3, publicada só em caso de sucesso da análise de IA. */
        private String resultsReadyQueueUrl;
        private Q1Consumer q1Consumer = new Q1Consumer();

        @Data
        public static class Q1Consumer {
            private boolean enabled = true;
            private long pollIntervalMs = 5000;
            private int waitTimeSeconds = 10;
            private int maxMessages = 10;
        }
    }

    @Data
    public static class S3 {
        private String endpoint;
        private String rawDocumentsBucket;
        /** Destino do JSON+Markdown do relatório final. */
        private String curatedDocumentsBucket;
    }
}
