package com.factoryapp.model;

public enum Priority {
    RUSH(1),
    HIGH(2),
    NORMAL(3);

    private final int order;

    Priority(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
