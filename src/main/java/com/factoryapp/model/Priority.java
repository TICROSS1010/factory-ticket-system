package com.factoryapp.model;

// Order priority levels — lower number = higher priority, used for sorting the ticket queue
public enum Priority {
    RUSH(1),    // Highest priority, always processed first
    HIGH(2),
    NORMAL(3);

    private final int order;

    Priority(int order) {
        this.order = order;
    }

    // Returns the sort value — lower means higher priority
    public int getOrder() {
        return order;
    }
}
