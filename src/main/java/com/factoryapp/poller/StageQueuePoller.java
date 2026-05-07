package com.factoryapp.poller;

import com.factoryapp.model.Order;
import com.factoryapp.model.Stage;
import com.factoryapp.repository.OrderRepository;
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

    // Queue URLs — will move to application properties later
    private static final String BASE_URL = "https://sqs.us-east-1.amazonaws.com/YOUR_ACCOUNT_ID";

    // Sales queues
    private static final String SALES_RUSH   = BASE_URL + "/sales-rush-queue.fifo";
    private static final String SALES_HIGH   = BASE_URL + "/sales-high-queue.fifo";
    private static final String SALES_NORMAL = BASE_URL + "/sales-normal-queue.fifo";

    // Line worker queues
    private static final String LINE_RUSH    = BASE_URL + "/line-rush-queue.fifo";
    private static final String LINE_HIGH    = BASE_URL + "/line-high-queue.fifo";
    private static final String LINE_NORMAL  = BASE_URL + "/line-normal-queue.fifo";

    // Quality queues
    private static final String QUALITY_RUSH   = BASE_URL + "/quality-rush-queue.fifo";
    private static final String QUALITY_HIGH   = BASE_URL + "/quality-high-queue.fifo";
    private static final String QUALITY_NORMAL = BASE_URL + "/quality-normal-queue.fifo";

    // Packer queues
    private static final String PACKER_RUSH   = BASE_URL + "/packer-rush-queue.fifo";
    private static final String PACKER_HIGH   = BASE_URL + "/packer-high-queue.fifo";
    private static final String PACKER_NORMAL = BASE_URL + "/packer-normal-queue.fifo";

    // Shipping queues
    private static final String SHIPPING_RUSH   = BASE_URL + "/shipping-rush-queue.fifo";
    private static final String SHIPPING_HIGH   = BASE_URL + "/shipping-high-queue.fifo";
    private static final String SHIPPING_NORMAL = BASE_URL + "/shipping-normal-queue.fifo";

    public StageQueuePoller(SqsClient sqsClient, OrderRepository orderRepository) {
        this.sqsClient = sqsClient;
        this.orderRepository = orderRepository;
    }

    // ── Poll every 5 seconds ──────────────────────────────────────────────
    @Scheduled(fixedDelay = 5000)
    public void poll() {
        pollStage(Stage.SALES,       SALES_RUSH,    SALES_HIGH,    SALES_NORMAL);
        pollStage(Stage.LINE_WORKER, LINE_RUSH,     LINE_HIGH,     LINE_NORMAL);
        pollStage(Stage.QUALITY,     QUALITY_RUSH,  QUALITY_HIGH,  QUALITY_NORMAL);
        pollStage(Stage.PACKER,      PACKER_RUSH,   PACKER_HIGH,   PACKER_NORMAL);
        pollStage(Stage.SHIPPING,    SHIPPING_RUSH, SHIPPING_HIGH, SHIPPING_NORMAL);
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

            return messages.isEmpty() ? null : messages.get(0);
        } catch (Exception e) {
            // Queue may not exist yet in dev — skip silently
            return null;
        }
    }

    // ── Process a received message ────────────────────────────────────────
    private void processMessage(Message message, Stage stage) {
        // Parse orderId from message body
        String orderId = message.body();

        // Save to DynamoDB so it appears on the worker screen
        Order order = orderRepository.findById(orderId);
        if (order != null) {
            order.setCurrentStage(stage);
            orderRepository.save(order);
        }

        // Delete message from queue — worker has received it
        // Queue URL stored in message attributes in full implementation
    }
}