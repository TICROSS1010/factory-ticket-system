package com.factoryapp.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@Profile("dev")  // only runs in dev
// Runs automatically on startup via CommandLineRunner — seeds SQS queues with test orders (dev only)
public class TestDataSeeder implements CommandLineRunner {

    private final SqsClient sqsClient;

    @Value("${aws.account-id}")
    private String accountId;

    @Value("${aws.region}")
    private String region;

    public TestDataSeeder(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    @Override
    public void run(String... args) {
        String[] priorities = {"rush", "high", "normal"};

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
}
