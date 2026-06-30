package com.example.demo.controller;

import com.example.demo.model.OrderItem;
import com.example.demo.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/kitchen")
@RequiredArgsConstructor
public class KitchenController {

    private final OrderItemRepository orderItemRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Fetch all pending order items
        List<OrderItem> pendingItems = orderItemRepository.findByStatus("PENDING");
        model.addAttribute("pendingItems", pendingItems);
        return "kitchen/dashboard";
    }

    @PostMapping("/ready/{id}")
    public String markAsReady(@PathVariable Long id) {
        orderItemRepository.findById(id).ifPresent(item -> {
            item.setStatus("READY");
            orderItemRepository.save(item);
        });
        return "redirect:/kitchen/dashboard";
    }
}
