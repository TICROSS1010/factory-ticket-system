package com.factoryapp.poller;

import com.factoryapp.model.Action;
import com.factoryapp.model.Order;
import com.factoryapp.model.OrderHistory;
import com.factoryapp.model.Stage;
import com.factoryapp.repository.OrderHistoryRepository;
import com.factoryapp.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.List;

// Polls all 15 SQS FIFO queues (5 stages x 3 priorities) every 5 seconds.
// When a message arrives it saves the order to DynamoDB and writes a CREATED history entry.
// Rush is always checked before High, High before Normal.
@Component
@EnableScheduling
public class StageQueuePoller {

    private final SqsClient sqsClient;
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;

    // Ignores unknown JSON fields so new order attributes don't break deserialization
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${aws.account-id}")
    private String accountId;

    @Value("${aws.region}")
    private String region;

    public StageQueuePoller(SqsClient sqsClient, OrderRepository orderRepository, OrderHistoryRepository orderHistoryRepository) {
        this.sqsClient = sqsClient;
        this.orderRepository = orderRepository;
        this.orderHistoryRepository = orderHistoryRepository;
    }

    // Builds the full SQS queue URL from stage and priority names
    private String queueUrl(String stage, String priority) {
        return "https://sqs." + region + ".amazonaws.com/" + accountId + "/" + stage + "-" + priority + "-queue.fifo";
    }

    // Runs every 5 seconds — polls one message per stage in priority order
    @Scheduled(fixedDelay = 5000)
    public void poll() {
        pollStage(Stage.SALES,       queueUrl("sales", "rush"),    queueUrl("sales", "high"),    queueUrl("sales", "normal"));
        pollStage(Stage.LINE_WORKER, queueUrl("line", "rush"),     queueUrl("line", "high"),     queueUrl("line", "normal"));
        pollStage(Stage.QUALITY,     queueUrl("quality", "rush"),  queueUrl("quality", "high"),  queueUrl("quality", "normal"));
        pollStage(Stage.PACKER,      queueUrl("packer", "rush"),   queueUrl("packer", "high"),   queueUrl("packer", "normal"));
        pollStage(Stage.SHIPPING,    queueUrl("shipping", "rush"), queueUrl("shipping", "high"), queueUrl("shipping", "normal"));
    }

    // Checks Rush → High → Normal and processes the first message found, then stops
    private void pollStage(Stage stage, String rush, String high, String normal) {
        for (String url : List.of(rush, high, normal)) {
            Message message = receiveOne(url);
            if (message != null) {
                processMessage(message, stage, url);
                return;
            }
        }
    }

    // Attempts to receive a single message from the given queue URL, returns null on failure
    private Message receiveOne(String queueUrl) {
        try {
            List<Message> messages = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(1)
                            .waitTimeSeconds(1)
                            .build()
            ).messages();

            return messages.isEmpty() ? null : messages.getFirst();
        } catch (Exception e) {
            return null;
        }
    }

    // Parses the SQS message, saves the order to DynamoDB, writes a CREATED history entry,
    // then deletes the message from the queue. If anything fails the message stays in SQS
    // and will be retried on the next poll cycle.
    private void processMessage(Message message, Stage stage, String queueUrl) {
        try {
            Order order = objectMapper.readValue(message.body(), Order.class);
            order.setCurrentStage(stage);
            order.setReturnCount(0);
            order.setUpdatedAt(order.getCreatedAt());

            orderRepository.save(order);

            // Record that this order entered the system
            orderHistoryRepository.save(new OrderHistory(
                    order.getOrderId(),
                    Instant.now().toString(),
                    stage,
                    "system",
                    Action.CREATED,
                    null
            ));

            // Only delete from SQS after a successful save — ensures at-least-once processing
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (Exception e) {
            System.err.println("Failed to process message: " + e.getMessage());
        }
    }
}
