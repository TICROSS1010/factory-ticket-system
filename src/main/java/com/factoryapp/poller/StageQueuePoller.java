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

@Component
@EnableScheduling
public class StageQueuePoller {

    private final SqsClient sqsClient;
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
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

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        pollStage(Stage.SALES,       queueUrl("sales", "rush"),    queueUrl("sales", "high"),    queueUrl("sales", "normal"));
        pollStage(Stage.LINE_WORKER, queueUrl("line", "rush"),     queueUrl("line", "high"),     queueUrl("line", "normal"));
        pollStage(Stage.QUALITY,     queueUrl("quality", "rush"),  queueUrl("quality", "high"),  queueUrl("quality", "normal"));
        pollStage(Stage.PACKER,      queueUrl("packer", "rush"),   queueUrl("packer", "high"),   queueUrl("packer", "normal"));
        pollStage(Stage.SHIPPING,    queueUrl("shipping", "rush"), queueUrl("shipping", "high"), queueUrl("shipping", "normal"));
    }

    private void pollStage(Stage stage, String rush, String high, String normal) {
        for (String url : List.of(rush, high, normal)) {
            Message message = receiveOne(url);
            if (message != null) {
                processMessage(message, stage, url);
                return;
            }
        }
    }

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

    private void processMessage(Message message, Stage stage, String queueUrl) {
        try {
            Order order = objectMapper.readValue(message.body(), Order.class);
            order.setCurrentStage(stage);
            order.setReturnCount(0); //fix later , this could prove an issue when gets to quality
            order.setUpdatedAt(order.getCreatedAt()); //may cause issues later?

            orderRepository.save(order);

            orderHistoryRepository.save(new OrderHistory(
                    order.getOrderId(),
                    Instant.now().toString(),
                    stage,
                    "system",
                    Action.CREATED,
                    null
            ));

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (Exception e) { //create DLQ if failed to process later
            System.err.println("Failed to process message: " + e.getMessage());
        }
    }
}
