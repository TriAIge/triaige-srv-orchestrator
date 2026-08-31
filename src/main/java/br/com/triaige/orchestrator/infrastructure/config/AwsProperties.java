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
    }
}
