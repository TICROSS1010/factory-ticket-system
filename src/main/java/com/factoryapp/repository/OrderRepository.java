package com.factoryapp.repository;

import com.factoryapp.model.Order;
import com.factoryapp.model.Priority;
import com.factoryapp.model.Stage;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class OrderRepository {

    private static final String TABLE_NAME = "orders";
    private final DynamoDbClient dynamoDbClient;

    public OrderRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    // ── Save or update an order ───────────────────────────────────────────
    public void save(Order order) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(toMap(order))
                .build());
    }

    // ── Get single order by ID ────────────────────────────────────────────
    public Order findById(String orderId) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("orderId", AttributeValue.fromS(orderId)))
                .build());

        if (!response.hasItem()) return null;
        return fromMap(response.item());
    }

    // ── Get all orders at a specific stage ────────────────────────────────
    public List<Order> findByStage(Stage stage) {
        ScanResponse response = dynamoDbClient.scan(ScanRequest.builder()
                .tableName(TABLE_NAME)
                .filterExpression("currentStage = :stage")
                .expressionAttributeValues(Map.of(
                        ":stage", AttributeValue.fromS(stage.name())
                ))
                .build());

        return response.items().stream()
                .map(this::fromMap)
                .collect(Collectors.toList());
    }

    // ── Convert Order → DynamoDB map ──────────────────────────────────────
    private Map<String, AttributeValue> toMap(Order order) {
        var map = new HashMap<String, AttributeValue>();

        map.put("orderId",      AttributeValue.fromS(order.getOrderId()));
        map.put("customer",     AttributeValue.fromS(order.getCustomer()));
        map.put("priority",     AttributeValue.fromS(order.getPriority().name()));
        map.put("orderType",    AttributeValue.fromS(order.getOrderType()));
        map.put("quantity",     AttributeValue.fromN(order.getQuantity().toString()));
        map.put("currentStage", AttributeValue.fromS(order.getCurrentStage().name()));
        map.put("returnCount",  AttributeValue.fromN(order.getReturnCount().toString()));
        map.put("createdAt",    AttributeValue.fromS(order.getCreatedAt()));
        map.put("dueDate",      AttributeValue.fromS(order.getDueDate()));
        map.put("updatedAt",    AttributeValue.fromS(order.getUpdatedAt()));

        if (order.getReturnReason()  != null) map.put("returnReason",  AttributeValue.fromS(order.getReturnReason()));
        if (order.getParentOrderId() != null) map.put("parentOrderId", AttributeValue.fromS(order.getParentOrderId()));
        if (order.getNotes()         != null) map.put("notes",         AttributeValue.fromS(order.getNotes()));
        if (order.getAssignedTo()    != null) map.put("assignedTo",    AttributeValue.fromS(order.getAssignedTo()));

        return map;
    }

    // ── Convert DynamoDB map → Order ──────────────────────────────────────
    private Order fromMap(Map<String, AttributeValue> item) {
        return Order.builder()
                .orderId(item.get("orderId").s())
                .customer(item.get("customer").s())
                .priority(Priority.valueOf(item.get("priority").s()))
                .orderType(item.get("orderType").s())
                .quantity(Integer.parseInt(item.get("quantity").n()))
                .currentStage(Stage.valueOf(item.get("currentStage").s()))
                .returnCount(Integer.parseInt(item.get("returnCount").n()))
                .createdAt(item.get("createdAt").s())
                .dueDate(item.get("dueDate").s())
                .updatedAt(item.get("updatedAt").s())
                .returnReason(item.containsKey("returnReason")  ? item.get("returnReason").s()  : null)
                .parentOrderId(item.containsKey("parentOrderId") ? item.get("parentOrderId").s() : null)
                .notes(item.containsKey("notes")                ? item.get("notes").s()         : null)
                .assignedTo(item.containsKey("assignedTo")      ? item.get("assignedTo").s()    : null)
                .build();
    }
}