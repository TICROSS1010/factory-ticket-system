package com.factoryapp.model;

// Order priority levels — lower number = higher priority, used for sorting the ticket queue
public enum Priority {
    RUSH(1),    // Highest priority, always processed first
    HIGH(2),
    NORMAL(3);

    private final int sortValue;

    Priority(int sortValue) {
        this.sortValue = sortValue;
    }

    public int getSortValue() {
        return sortValue;
    }
}
