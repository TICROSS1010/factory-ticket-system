package com.factoryapp.model;


public record OrderHistory(
        String orderId,
        String timestamp,
        Stage stage,
        String workerId,
        Action action,
        String notes
) {}