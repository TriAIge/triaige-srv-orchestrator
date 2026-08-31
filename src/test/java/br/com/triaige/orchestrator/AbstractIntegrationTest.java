package br.com.triaige.orchestrator;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import java.net.URI;

/**
 * Base de testes de integração: MySQL (para as migrações Flyway reais, incluindo a
 * sintaxe MySQL-específica de protocol_sequences) + LocalStack (SQS + S3).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final String RAW_BUCKET = "bucket-triaige-raw-certificacoes-test";
    protected static final String DOCS_RECEIVED_QUEUE = "triaige-docs-received-test";
    protected static final String DOCS_PREPROCESSING_QUEUE = "triaige-docs-preprocessing-test";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.3"))
            .withDatabaseName("triaige_srv_orchestrator")
            .withUsername("triaige")
            .withPassword("triaige");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.4"))
            .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.S3);

    private static String docsReceivedQueueUrl;
    private static String docsPreprocessingQueueUrl;

    @LocalServerPort
    protected int port;

    protected final TestRestTemplate restTemplate = new TestRestTemplate();

    /** Token de teste seedado por V3__seed_dev_data.sql. */
    protected static final String TEST_TOKEN = "dev-local-token";

    @BeforeAll
    static void provisionAwsResources() {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .forcePathStyle(true)
                .build();
        s3Client.createBucket(b -> b.bucket(RAW_BUCKET));

        SqsClient sqsClient = SqsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
        docsReceivedQueueUrl = sqsClient.createQueue(
                CreateQueueRequest.builder().queueName(DOCS_RECEIVED_QUEUE).build()).queueUrl();
        docsPreprocessingQueueUrl = sqsClient.createQueue(
                CreateQueueRequest.builder().queueName(DOCS_PREPROCESSING_QUEUE).build()).queueUrl();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("aws.region", localstack::getRegion);
        registry.add("aws.s3.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("aws.sqs.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        registry.add("aws.s3.raw-documents-bucket", () -> RAW_BUCKET);
        registry.add("aws.sqs.docs-received-queue-url", () -> docsReceivedQueueUrl);
        registry.add("aws.sqs.docs-preprocessing-queue-url", () -> docsPreprocessingQueueUrl);
        // Evita interferência entre o consumidor de Q1 e as asserções síncronas dos testes.
        registry.add("aws.sqs.q1-consumer.enabled", () -> "false");
    }

    protected String baseUrl() {
        return "http://localhost:" + port + "/api/orchestrator/v1";
    }
}
