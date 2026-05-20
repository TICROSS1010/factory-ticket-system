package com.factoryapp.controller;

import com.factoryapp.model.Action;
import com.factoryapp.model.Stage;
import com.factoryapp.repository.OrderRepository;
import com.factoryapp.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;

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

    // ── Show tickets for logged in role ───────────────────────────────────
    @GetMapping("/tickets")
    public String tickets(Authentication auth, Model model) {
        Stage stage = getStageForRole(auth);

        var tickets = orderRepository.findByStage(stage);

        tickets.sort(Comparator
                .comparingInt(o -> o.getPriority().getOrder())
        );

        model.addAttribute("tickets", tickets);
        model.addAttribute("role", getRoleName(auth));

        return "tickets";
    }

    // ── Handle action button click ────────────────────────────────────────
    @PostMapping("/tickets/{orderId}/action")
    public String handleAction(
            @PathVariable String orderId,
            @RequestParam String action,
            Authentication auth) {

        orderService.processAction(orderId, Action.valueOf(action), auth.getName());
        return "redirect:/tickets";
    }

    // ── Map role → stage ──────────────────────────────────────────────────
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

    // ── Extract role name from auth ───────────────────────────────────────
    private String getRoleName(Authentication auth) {
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElseThrow();
    }
}