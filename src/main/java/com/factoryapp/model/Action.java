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
    HOLD,             // Line worker put the order on hold
    RESUMED,          // Line worker resumed work after a hold
    RESET,            // Line worker reset all work — order returns to unstarted
    COMMENT,          // Worker added a comment — stored in orderHistory notes field
    PACKED,           // Packer confirmed packed
    SHIPPED,          // Shipping confirmed sent
    DELIVERED         // Final status — order delivered
}