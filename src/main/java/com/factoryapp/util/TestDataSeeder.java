package com.factoryapp.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import org.springframework.beans.factory.DisposableBean;

@Component
@Profile("dev")  // only runs in dev
// Runs automatically on startup via CommandLineRunner — seeds SQS queues with test orders (dev only)
public class TestDataSeeder implements CommandLineRunner, DisposableBean {

    private final SqsClient sqsClient;

    @Value("${aws.account-id}")
    private String accountId;

    @Value("${aws.region}")
    private String region;

    public TestDataSeeder(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    // Purges all 15 queues — called on startup and shutdown
    private void purgeAllQueues() {
        String[] stages = {"sales", "line", "quality", "packer", "shipping"};
        String[] priorities = {"rush", "high", "normal"};

        for (String stage : stages) {
            for (String priority : priorities) {
                String queueUrl = "https://sqs." + region + ".amazonaws.com/" + accountId + "/" + stage + "-" + priority + "-queue.fifo";
                try {
                    sqsClient.purgeQueue(PurgeQueueRequest.builder()
                            .queueUrl(queueUrl)
                            .build());
                    System.out.println("Purged: " + stage + "-" + priority);
                } catch (Exception e) {
                    System.out.println("Could not purge " + stage + "-" + priority + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void run(String... args) {
        String[] priorities = {"rush", "high", "normal"};

        // Purge all queues before seeding
        purgeAllQueues();

        // Seed test orders
        for (int i = 1; i <= 10; i++) {
            String priority = priorities[i % 3];
            String queueUrl = "https://sqs." + region + ".amazonaws.com/" + accountId + "/sales-" + priority + "-queue.fifo";

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody("ORD-" + String.format("%04d", i))
                    .messageGroupId("orders")
                    .messageDeduplicationId("test-" + i)
                    .build());

            System.out.println("Sent ORD-" + String.format("%04d", i) + " to " + priority);
        }
    }

    @Override
    public void destroy() {
        purgeAllQueues();
    }
}
