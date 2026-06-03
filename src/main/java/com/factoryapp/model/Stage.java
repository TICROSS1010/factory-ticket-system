package com.factoryapp.model;

// Represents each stage an order moves through in the factory workflow
public enum Stage {
    SALES,        // Incoming order awaiting sales confirmation
    LINE_WORKER,  // Confirmed order in production
    QUALITY,      // Finished order undergoing quality check
    PACKER,       // Passed order being packed
    SHIPPING,     // Packed order ready to ship
    DELIVERED,    // Order successfully shipped and delivered
    CANCELLED     // Order rejected by sales
}
