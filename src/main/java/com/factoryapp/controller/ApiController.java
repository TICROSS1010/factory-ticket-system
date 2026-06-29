package com.factoryapp.controller;

import com.factoryapp.model.Action;
import com.factoryapp.model.Order;
import com.factoryapp.model.Stage;
import com.factoryapp.repository.OrderRepository;
import com.factoryapp.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {

    static final int MAX_TEXT_LENGTH = 500;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public ApiController(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @GetMapping("/me")
    public Map<String, String> me(Authentication auth) {
        return Map.of("username", auth.getName(), "role", roleName(auth));
    }

    @GetMapping("/tickets")
    public List<Order> tickets(Authentication auth) {
        var tickets = orderRepository.findByStage(stageForRole(auth));
        tickets.sort(Comparator.comparingInt(o -> o.getPriority().getSortValue()));
        return tickets;
    }

    @PostMapping("/tickets/{orderId}/action")
    public ResponseEntity<Map<String, String>> action(
            @PathVariable String orderId,
            @RequestBody ActionRequest req,
            Authentication auth) {

        Action act = Action.valueOf(req.action());

        if (act == Action.FAILED && (req.reason() == null || req.reason().isBlank()))
            return ResponseEntity.badRequest().body(Map.of("error", "fail-reason-required"));

        if (req.reason() != null && req.reason().length() > MAX_TEXT_LENGTH)
            return ResponseEntity.badRequest().body(Map.of("error", "comment-too-long"));

        orderService.processAction(orderId, act, auth.getName(),
                req.reason() != null ? req.reason().trim() : null);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/tickets/{orderId}/comment")
    public ResponseEntity<Map<String, String>> comment(
            @PathVariable String orderId,
            @RequestBody CommentRequest req,
            Authentication auth) {

        if (req.text() == null || req.text().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "empty-comment"));

        if (req.text().length() > MAX_TEXT_LENGTH)
            return ResponseEntity.badRequest().body(Map.of("error", "comment-too-long"));

        orderService.addComment(orderId, req.text().trim(), auth.getName());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/tickets/{orderId}/comments")
    public List<Map<String, String>> comments(@PathVariable String orderId) {
        return orderService.getComments(orderId).stream()
                .map(h -> Map.of(
                        "worker",    h.workerId(),
                        "text",      h.notes() != null ? h.notes() : "",
                        "timestamp", h.timestamp(),
                        "type",      h.action().name()
                ))
                .collect(Collectors.toList());
    }

    private Stage stageForRole(Authentication auth) {
        return switch (roleName(auth)) {
            case "SALES"       -> Stage.SALES;
            case "LINE_WORKER" -> Stage.LINE_WORKER;
            case "QUALITY"     -> Stage.QUALITY;
            case "PACKER"      -> Stage.PACKER;
            case "SHIPPING"    -> Stage.SHIPPING;
            default -> throw new IllegalStateException("Unknown role: " + roleName(auth));
        };
    }

    private String roleName(Authentication auth) {
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> Objects.requireNonNull(a.getAuthority()).replace("ROLE_", ""))
                .orElseThrow();
    }

    record ActionRequest(String action, String reason) {}
    record CommentRequest(String text) {}
}
