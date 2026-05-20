package com.factoryapp.service;

import com.factoryapp.model.*;
import com.factoryapp.repository.OrderHistoryRepository;
import com.factoryapp.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;

    public OrderService(OrderRepository orderRepository, OrderHistoryRepository orderHistoryRepository) {
        this.orderRepository = orderRepository;
        this.orderHistoryRepository = orderHistoryRepository;
    }

    public void processAction(String orderId, Action action, String workerId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found: " + orderId);

        Stage stageAtAction = order.getCurrentStage();
        Stage nextStage = nextStage(action);

        order.setCurrentStage(nextStage);
        order.setAssignedTo(workerId);
        order.setUpdatedAt(Instant.now().toString());

        if (action == Action.FAILED) {
            order.setPriority(Priority.RUSH);
            order.setReturnCount(order.getReturnCount() + 1);
        }

        orderRepository.save(order);

        orderHistoryRepository.save(new OrderHistory(
                orderId,
                Instant.now().toString(),
                stageAtAction,
                workerId,
                action,
                null
        ));
    }

    private Stage nextStage(Action action) {
        return switch (action) {
            case CONFIRMED -> Stage.LINE_WORKER;
            case REJECTED  -> Stage.CANCELLED;
            case STARTED   -> Stage.LINE_WORKER;
            case COMPLETED -> Stage.QUALITY;
            case PASSED    -> Stage.PACKER;
            case FAILED    -> Stage.LINE_WORKER;
            case PACKED    -> Stage.SHIPPING;
            case SHIPPED   -> Stage.DELIVERED;
            default        -> throw new IllegalArgumentException("Unhandled action: " + action);
        };
    }
}
