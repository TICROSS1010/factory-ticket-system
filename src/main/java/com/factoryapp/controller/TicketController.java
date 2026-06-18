package com.factoryapp.controller;

import com.factoryapp.model.Action;
import com.factoryapp.model.Stage;
import com.factoryapp.repository.OrderRepository;
import com.factoryapp.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

// Kept for backward-compat form submissions. React UI uses ApiController (/api/**) instead.
@Controller
public class TicketController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public TicketController(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @PostMapping("/tickets/{orderId}/action")
    public String handleAction(
            @PathVariable String orderId,
            @RequestParam String action,
            @RequestParam(required = false) String reason,
            Authentication auth) {

        Action act = Action.valueOf(action);
        if (act == Action.FAILED && (reason == null || reason.isBlank()))
            return "redirect:/tickets?error=fail-reason-required";

        orderService.processAction(orderId, act, auth.getName(),
                reason != null ? reason.trim() : null);
        return "redirect:/";
    }

    @PostMapping("/tickets/{orderId}/comment")
    public String addComment(
            @PathVariable String orderId,
            @RequestParam String text,
            Authentication auth) {

        if (text != null && !text.isBlank())
            orderService.addComment(orderId, text.trim(), auth.getName());
        return "redirect:/";
    }
}