package com.factoryapp.service;

import com.factoryapp.model.*;
import com.factoryapp.repository.OrderHistoryRepository;
import com.factoryapp.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

// Handles all order state transitions — updates DynamoDB and writes an audit history entry.
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;

    public OrderService(OrderRepository orderRepository, OrderHistoryRepository orderHistoryRepository) {
        this.orderRepository = orderRepository;
        this.orderHistoryRepository = orderHistoryRepository;
    }

    // Applies a worker action to an order: moves it to the next stage and records the event
    public void processAction(String orderId, Action action, String workerId, String reason) {
        Order order = orderRepository.findById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found: " + orderId);

        // Capture the current stage before mutating — history entry logs where the action happened
        Stage stageAtAction = order.getCurrentStage();
        Stage nextStage = nextStage(action);

        order.setCurrentStage(nextStage);
        order.setAssignedTo(stageAtAction == nextStage ? workerId : null);
        order.setUpdatedAt(Instant.now().toString());

        // Quality failure sends the order back to line worker as RUSH regardless of original priority
        if (action == Action.FAILED) {
            order.setPriority(Priority.RUSH);
            order.setReturnCount(order.getReturnCount() + 1);
            order.setReturnReason(reason);
        }

        // Track in-progress state for line worker so the UI can show the right buttons
        switch (action) {
            case STARTED  -> order.setWorkStatus("IN_PROGRESS");
            case HOLD     -> order.setWorkStatus("ON_HOLD");
            case RESUMED  -> order.setWorkStatus("IN_PROGRESS");
            case RESET, COMPLETED, FAILED -> order.setWorkStatus(null);
            default -> { /* no workStatus change for other roles */ }
        }

        orderRepository.save(order);

        // Write an immutable audit record of this action to the history table
        orderHistoryRepository.save(new OrderHistory(
                orderId,
                Instant.now().toString(),
                stageAtAction,
                workerId,
                action,
                reason
        ));
    }

    // Returns all comments and QC rejection entries for an order, oldest first
    public List<OrderHistory> getComments(String orderId) {
        return orderHistoryRepository.findByOrderId(orderId).stream()
                .filter(h -> h.action() == Action.COMMENT ||
                             (h.action() == Action.FAILED && h.notes() != null))
                .collect(java.util.stream.Collectors.toList());
    }

    // Appends a comment to the order's history and stamps the order so others see the indicator
    public void addComment(String orderId, String text, String workerId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found: " + orderId);

        String timestamp = Instant.now().toString();
        order.setLastCommentAt(timestamp);
        order.setLastCommentBy(workerId);
        orderRepository.save(order);

        orderHistoryRepository.save(new OrderHistory(
                orderId,
                timestamp,
                order.getCurrentStage(),
                workerId,
                Action.COMMENT,
                text
        ));
    }

    // Maps each worker action to the stage the order moves to next
    private Stage nextStage(Action action) {
        return switch (action) {
            case CONFIRMED -> Stage.LINE_WORKER;
            case REJECTED  -> Stage.CANCELLED;
            case STARTED   -> Stage.LINE_WORKER;  // stays at LINE_WORKER, just logged
            case HOLD      -> Stage.LINE_WORKER;  // stays at LINE_WORKER, marked on hold
            case RESUMED   -> Stage.LINE_WORKER;  // stays at LINE_WORKER, back in progress
            case RESET     -> Stage.LINE_WORKER;  // stays at LINE_WORKER, back to unstarted
            case COMPLETED -> Stage.QUALITY;
            case PASSED    -> Stage.PACKER;
            case FAILED    -> Stage.LINE_WORKER;  // returned to line as RUSH
            case PACKED    -> Stage.SHIPPING;
            case SHIPPED   -> Stage.DELIVERED;
            default        -> throw new IllegalArgumentException("Unhandled action: " + action);
        };
    }
}
