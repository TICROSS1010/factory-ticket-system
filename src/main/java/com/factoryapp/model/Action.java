package com.factoryapp.model;

public enum Action {
    CREATED,          // Order received into the system
    CONFIRMED,        // Sales confirmed the order
    REJECTED,         // Sales rejected the order
    STARTED,          // Line worker started production
    COMPLETED,        // Line worker finished production
    PASSED,           // Quality passed the order
    FAILED,           // Quality failed the order
    RETURNED_TO_LINE, // Order sent back to line worker after quality fail
    PACKED,           // Packer confirmed packed
    SHIPPED,          // Shipping confirmed sent
    DELIVERED         // Final status — order delivered
}