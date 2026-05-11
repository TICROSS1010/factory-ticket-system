package com.factoryapp.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import org.springframework.beans.factory.DisposableBean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
@Profile("dev")
public class TestDataSeeder implements CommandLineRunner, DisposableBean {

    private final SqsClient sqsClient;
    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.account-id}")
    private String accountId;

    @Value("${aws.region}")
    private String region;

    private static final String[] CUSTOMERS = {
            "Acme Corp", "Globex Inc", "Initech", "Umbrella Ltd", "Stark Industries",
            "Wayne Enterprises", "Oscorp", "Cyberdyne", "Weyland Corp", "Soylent Corp"
    };

    private static final String[] ORDER_TYPES = {"standard", "custom", "rush-build"};
    private static final String[] PRIORITIES   = {"rush", "high", "normal"};

    public TestDataSeeder(SqsClient sqsClient, DynamoDbClient dynamoDbClient) {
        this.sqsClient = sqsClient;
        this.dynamoDbClient = dynamoDbClient;
    }

    private void purgeAllQueues() {
        String[] stages = {"sales", "line", "quality", "packer", "shipping"};

        for (String stage : stages) {
            for (String priority : PRIORITIES) {
                String queueUrl = "https://sqs." + region + ".amazonaws.com/" + accountId + "/" + stage + "-" + priority + "-queue.fifo";
                try {
                    sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
                    System.out.println("Purged: " + stage + "-" + priority);
                } catch (Exception e) {
                    System.out.println("Could not purge " + stage + "-" + priority + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void run(String... args) {
        purgeAllQueues();
        purgeAllDynamoTables();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String now = LocalDateTime.now().format(fmt);
        String dueDate = LocalDateTime.now().plusDays(7).format(fmt);

        for (int i = 1; i <= 10; i++) {
            String priority   = PRIORITIES[i % 3];
            String orderId    = "ORD-" + String.format("%04d", i);
            String customer   = CUSTOMERS[i - 1];
            String orderType  = ORDER_TYPES[i % 3];
            int    quantity   = i * 5;
            String queueUrl   = "https://sqs." + region + ".amazonaws.com/" + accountId + "/sales-" + priority + "-queue.fifo";

            String body = String.format(
                    "{\"orderId\":\"%s\",\"customer\":\"%s\",\"priority\":\"%s\",\"orderType\":\"%s\",\"quantity\":%d,\"dueDate\":\"%s\",\"createdAt\":\"%s\"}",
                    orderId, customer, priority.toUpperCase(), orderType, quantity, dueDate, now
            );

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .messageGroupId("orders")
                    .messageDeduplicationId("test-" + i)
                    .build());

            System.out.println("Sent " + orderId + " (" + priority + ") to sales queue");
        }
    }

    private void purgeAllDynamoTables() {
        purgeTable("orders",       "orderId", null);
        purgeTable("orderHistory", "orderId", "timestamp");
    }

    private void purgeTable(String tableName, String partitionKey, String sortKey) {
        try {
            ScanResponse scan = dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build());
            for (Map<String, AttributeValue> item : scan.items()) {
                var key = new java.util.HashMap<String, AttributeValue>();
                key.put(partitionKey, item.get(partitionKey));
                if (sortKey != null) key.put(sortKey, item.get(sortKey));
                dynamoDbClient.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build());
            }
            System.out.println("Purged DynamoDB table: " + tableName);
        } catch (Exception e) {
            System.out.println("Could not purge table " + tableName + ": " + e.getMessage());
        }
    }

    @Override
    public void destroy() {
        purgeAllQueues();
        purgeAllDynamoTables();
    }
}
