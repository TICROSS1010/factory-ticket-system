package com.factoryapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Represents a factory order as it moves through the production workflow.
// Stored in the DynamoDB "orders" table with orderId as the partition key.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private String orderId;
    private String customer;
    private Priority priority;       // RUSH / HIGH / NORMAL — controls queue position
    private String orderType;
    private Integer quantity;
    private Stage currentStage;      // Which stage the order is currently at
    private Integer returnCount;     // How many times quality has failed and returned this order
    private String returnReason;     // Reason provided when quality fails an order
    private String parentOrderId;    // Set if this order was split from a parent order
    private String createdAt;        // ISO 8601 timestamp
    private String dueDate;          // ISO 8601 timestamp
    private String notes;
    private String assignedTo;       // Username of the last worker who acted on this order
    private String updatedAt;        // ISO 8601 timestamp of the last stage change
    private String workStatus;       // LINE_WORKER in-progress state: null=PENDING, IN_PROGRESS, ON_HOLD
    private String lastCommentAt;    // Timestamp of the most recent comment
    private String lastCommentBy;    // Username who wrote the most recent comment
}
