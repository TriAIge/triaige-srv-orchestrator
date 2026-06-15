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
        private String docsPreprocessingQueueUrl;
        private String resultsReadyQueueUrl;
    }

    @Data
    public static class S3 {
        private String endpoint;
        private String rawDocumentsBucket;
        private String curatedResultsBucket;
    }
}
