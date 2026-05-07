package com.factoryapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private String orderId;
    private String customer;
    private Priority priority;
    private String orderType;
    private Integer quantity;
    private Stage currentStage;
    private Integer returnCount; //amount of times returned
    private String returnReason;
    private String parentOrderId;
    private String createdAt;
    private String dueDate;
    private String notes;
    private String assignedTo;
    private String updatedAt;
}
