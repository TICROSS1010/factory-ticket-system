package com.factoryapp.poller;

import com.factoryapp.model.Order;
import com.factoryapp.model.Stage;
import com.factoryapp.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

@Component
@EnableScheduling
public class StageQueuePoller {

    private final SqsClient sqsClient;
    private final OrderRepository orderRepository;

    @Value("${aws.account-id}")
    private String accountId;

    @Value("${aws.region}")
    private String region;

    public StageQueuePoller(SqsClient sqsClient, OrderRepository orderRepository) {
        this.sqsClient = sqsClient;
        this.orderRepository = orderRepository;
    }

    // Builds queue URL from stage and priority
    private String queueUrl(String stage, String priority) {
        return "https://sqs." + region + ".amazonaws.com/" + accountId + "/" + stage + "-" + priority + "-queue.fifo";
    }

    // ── Poll every 5 seconds ──────────────────────────────────────────────
    @Scheduled(fixedDelay = 5000)
    public void poll() {
        pollStage(Stage.SALES,       queueUrl("sales", "rush"),    queueUrl("sales", "high"),    queueUrl("sales", "normal"));
        pollStage(Stage.LINE_WORKER, queueUrl("line", "rush"),     queueUrl("line", "high"),     queueUrl("line", "normal"));
        pollStage(Stage.QUALITY,     queueUrl("quality", "rush"),  queueUrl("quality", "high"),  queueUrl("quality", "normal"));
        pollStage(Stage.PACKER,      queueUrl("packer", "rush"),   queueUrl("packer", "high"),   queueUrl("packer", "normal"));
        pollStage(Stage.SHIPPING,    queueUrl("shipping", "rush"), queueUrl("shipping", "high"), queueUrl("shipping", "normal"));
    }

    // ── Poll Rush first, then High, then Normal ───────────────────────────
    private void pollStage(Stage stage, String rush, String high, String normal) {
        Message message = receiveOne(rush);

        if (message == null) message = receiveOne(high);
        if (message == null) message = receiveOne(normal);

        if (message != null) {
            processMessage(message, stage);
        }
    }

    // ── Receive one message from a queue ──────────────────────────────────
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

    // ── Process a received message ────────────────────────────────────────
    private void processMessage(Message message, Stage stage) {
        String orderId = message.body();

        Order order = orderRepository.findById(orderId);
        if (order != null) {
            order.setCurrentStage(stage);
            orderRepository.save(order);
        }
    }
}