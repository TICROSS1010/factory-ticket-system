package com.factoryapp.controller;

import com.factoryapp.model.Action;
import com.factoryapp.model.OrderHistory;
import com.factoryapp.model.Stage;
import com.factoryapp.repository.OrderRepository;
import com.factoryapp.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Single controller for all worker roles. Each role sees only the orders at their stage.
@Controller
public class TicketController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public TicketController(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    // ── Show login page ───────────────────────────────────────────────────
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    static final int COMMENT_MAX_LENGTH = 500;

    // ── Show tickets for the logged-in worker's stage, sorted by priority ─
    @GetMapping("/tickets")
    public String tickets(Authentication auth, Model model,
                          @RequestParam(required = false) String error) {
        Stage stage = getStageForRole(auth);

        var tickets = orderRepository.findByStage(stage);

        // RUSH first, then HIGH, then NORMAL
        tickets.sort(Comparator
                .comparingInt(o -> o.getPriority().getOrder())
        );

        model.addAttribute("tickets", tickets);
        model.addAttribute("role", getRoleName(auth));
        model.addAttribute("currentUser", auth.getName());
        model.addAttribute("commentMaxLength", COMMENT_MAX_LENGTH);
        model.addAttribute("error", error);

        return "tickets";
    }

    // ── Handle action button (Confirm, Reject, Start, Complete, etc.) ─────
    @PostMapping("/tickets/{orderId}/action")
    public String handleAction(
            @PathVariable String orderId,
            @RequestParam String action,
            @RequestParam(required = false) String reason,
            Authentication auth) {

        Action act = Action.valueOf(action);

        if (act == Action.FAILED && (reason == null || reason.isBlank()))
            return "redirect:/tickets?error=fail-reason-required";

        if (reason != null && reason.length() > COMMENT_MAX_LENGTH)
            return "redirect:/tickets?error=comment-too-long";

        orderService.processAction(orderId, act, auth.getName(),
                reason != null ? reason.trim() : null);
        return "redirect:/tickets";
    }

    // ── Return comment + QC rejection history for an order as JSON ────────
    @GetMapping("/tickets/{orderId}/comments")
    @ResponseBody
    public List<Map<String, String>> getComments(@PathVariable String orderId) {
        return orderService.getComments(orderId).stream()
                .map(h -> Map.of(
                        "worker",    h.workerId(),
                        "text",      h.notes() != null ? h.notes() : "",
                        "timestamp", h.timestamp(),
                        "type",      h.action().name()
                ))
                .collect(Collectors.toList());
    }

    // ── Handle comment submission ──────────────────────────────────────────
    @PostMapping("/tickets/{orderId}/comment")
    public String addComment(
            @PathVariable String orderId,
            @RequestParam String text,
            Authentication auth) {

        if (text == null || text.isBlank()) return "redirect:/tickets";
        if (text.length() > COMMENT_MAX_LENGTH) return "redirect:/tickets?error=comment-too-long";

        orderService.addComment(orderId, text.trim(), auth.getName());
        return "redirect:/tickets";
    }

    // ── Resolve the worker's role to their corresponding factory stage ────
    private Stage getStageForRole(Authentication auth) {
        String role = getRoleName(auth);
        return switch (role) {
            case "SALES"       -> Stage.SALES;
            case "LINE_WORKER" -> Stage.LINE_WORKER;
            case "QUALITY"     -> Stage.QUALITY;
            case "PACKER"      -> Stage.PACKER;
            case "SHIPPING"    -> Stage.SHIPPING;
            default -> throw new IllegalStateException("Unknown role: " + role);
        };
    }

    // ── Strip the "ROLE_" prefix Spring Security adds to authority names ──
    private String getRoleName(Authentication auth) {
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElseThrow();
    }
}