package com.factoryapp.poller;

import com.factoryapp.model.Action;
import com.factoryapp.model.Order;
import com.factoryapp.model.OrderHistory;
import com.factoryapp.model.Stage;
import com.factoryapp.repository.OrderHistoryRepository;
import com.factoryapp.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Polls all 15 SQS FIFO queues (5 stages x 3 priorities) every cycle.
// Each stage polls concurrently. Rush/High are checked instantly; Normal uses a
// 20-second long poll so the thread parks efficiently when nothing urgent exists.
@Component
public class StageQueuePoller {

    private final SqsClient sqsClient;
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final ExecutorService stageExecutor = Executors.newFixedThreadPool(5);

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

    private String queueUrl(String stage, String priority) {
        return "https://sqs." + region + ".amazonaws.com/" + accountId + "/" + stage + "-" + priority + "-queue.fifo";
    }

    // Each stage runs on its own thread. fixedDelay restarts 100ms after all stages finish,
    // so when queues are empty the cycle naturally sleeps ~20s (blocked on the Normal long poll).
    @Scheduled(fixedDelay = 100)
    public void poll() {
        List<CompletableFuture<Void>> futures = List.of(
            CompletableFuture.runAsync(() -> pollStage(Stage.SALES,       queueUrl("sales", "rush"),    queueUrl("sales", "high"),    queueUrl("sales", "normal")),    stageExecutor),
            CompletableFuture.runAsync(() -> pollStage(Stage.LINE_WORKER, queueUrl("line", "rush"),     queueUrl("line", "high"),     queueUrl("line", "normal")),     stageExecutor),
            CompletableFuture.runAsync(() -> pollStage(Stage.QUALITY,     queueUrl("quality", "rush"),  queueUrl("quality", "high"),  queueUrl("quality", "normal")),  stageExecutor),
            CompletableFuture.runAsync(() -> pollStage(Stage.PACKER,      queueUrl("packer", "rush"),   queueUrl("packer", "high"),   queueUrl("packer", "normal")),   stageExecutor),
            CompletableFuture.runAsync(() -> pollStage(Stage.SHIPPING,    queueUrl("shipping", "rush"), queueUrl("shipping", "high"), queueUrl("shipping", "normal")), stageExecutor)
        );
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    // Drains Rush and High before dropping to Normal long poll.
    // Keeps cycling as long as urgent work exists; only parks on Normal when both are empty.
    private void pollStage(Stage stage, String rush, String high, String normal) {
        while (true) {
            QueueMessage qm = receive(rush, 0);
            if (qm == null) qm = receive(high, 0);
            if (qm != null) {
                processMessage(qm.message(), stage, qm.queueUrl());
                continue;
            }
            qm = receive(normal, 20);
            if (qm != null) processMessage(qm.message(), stage, qm.queueUrl());
            break;
        }
    }

    private QueueMessage receive(String queueUrl, int waitSeconds) {
        try {
            List<Message> messages = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(1)
                            .waitTimeSeconds(waitSeconds)
                            .build()
            ).messages();
            return messages.isEmpty() ? null : new QueueMessage(messages.getFirst(), queueUrl);
        } catch (Exception e) {
            return null;
        }
    }

    private void processMessage(Message message, Stage stage, String queueUrl) {
        try {
            Order order = objectMapper.readValue(message.body(), Order.class);
            order.setCurrentStage(stage);
            order.setReturnCount(0);
            order.setUpdatedAt(order.getCreatedAt());

            orderRepository.save(order);

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

    @PreDestroy
    public void shutdown() {
        stageExecutor.shutdownNow();
    }

    private record QueueMessage(Message message, String queueUrl) {}
}
