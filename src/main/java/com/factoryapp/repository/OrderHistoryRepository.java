package com.factoryapp.repository;

import com.factoryapp.model.Action;
import com.factoryapp.model.OrderHistory;
import com.factoryapp.model.Stage;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Data access layer for the "orderHistory" DynamoDB table.
// orderId (PK) + timestamp (SK) together uniquely identify each history entry.
// Entries are append-only — never updated or deleted.
@Repository
public class OrderHistoryRepository {

    private static final String TABLE_NAME = "orderHistory";
    private final DynamoDbClient dynamoDbClient;

    public OrderHistoryRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    // ── Append a new history entry ────────────────────────────────────────
    public void save(OrderHistory history) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(toMap(history))
                .build());
    }

    // ── Get full history for an order, oldest entry first ────────────────
    public List<OrderHistory> findByOrderId(String orderId) {
        QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("orderId = :orderId")
                .expressionAttributeValues(Map.of(
                        ":orderId", AttributeValue.fromS(orderId)
                ))
                .scanIndexForward(true) // oldest first
                .build());

        return response.items().stream()
                .map(this::fromMap)
                .collect(Collectors.toList());
    }

    // ── Get the most recent history entry for an order ───────────────────
    public OrderHistory findLatestByOrderId(String orderId) {
        QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("orderId = :orderId")
                .expressionAttributeValues(Map.of(
                        ":orderId", AttributeValue.fromS(orderId)
                ))
                .scanIndexForward(false) // newest first
                .limit(1)
                .build());

        return response.items().isEmpty() ? null : fromMap(response.items().get(0));
    }

    // ── Convert OrderHistory → DynamoDB attribute map ────────────────────
    private Map<String, AttributeValue> toMap(OrderHistory history) {
        var map = new HashMap<String, AttributeValue>();

        map.put("orderId",   AttributeValue.fromS(history.orderId()));
        map.put("timestamp", AttributeValue.fromS(history.timestamp()));
        map.put("stage",     AttributeValue.fromS(history.stage().name()));
        map.put("workerId",  AttributeValue.fromS(history.workerId()));
        map.put("action",    AttributeValue.fromS(history.action().name()));

        if (history.notes() != null) {
            map.put("notes", AttributeValue.fromS(history.notes()));
        }

        return map;
    }

    // ── Convert DynamoDB attribute map → OrderHistory ─────────────────────
    private OrderHistory fromMap(Map<String, AttributeValue> item) {
        return new OrderHistory(
                item.get("orderId").s(),
                item.get("timestamp").s(),
                Stage.valueOf(item.get("stage").s()),
                item.get("workerId").s(),
                Action.valueOf(item.get("action").s()),
                item.containsKey("notes") ? item.get("notes").s() : null
        );
    }
}