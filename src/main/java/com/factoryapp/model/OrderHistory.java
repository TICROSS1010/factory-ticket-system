package com.factoryapp.model;


// Immutable record of a single action taken on an order at a given stage.
// Stored in the DynamoDB "orderHistory" table with orderId (PK) + timestamp (SK).
public record OrderHistory(
        String orderId,   // Links back to the order in the orders table
        String timestamp, // ISO 8601 — used as the sort key to replay history in order
        Stage stage,      // The stage the order was at when this action was taken
        String workerId,  // Username of the worker, or "system" for automated actions
        Action action,    // What happened (CREATED, CONFIRMED, FAILED, etc.)
        String notes      // Optional worker note attached to this action
) {}