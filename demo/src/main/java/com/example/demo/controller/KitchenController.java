package com.example.demo.controller;

import com.example.demo.model.OrderItem;
import com.example.demo.service.KitchenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/kitchen")
@RequiredArgsConstructor
public class KitchenController {

    private final KitchenService kitchenService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<OrderItem> pendingItems = kitchenService.pendingItemsForCurrentBranch();
        model.addAttribute("pendingItems", pendingItems);
        return "kitchen/dashboard";
    }

    @PostMapping("/ready/{id}")
    public String markAsReady(@PathVariable Long id) {
        kitchenService.markReady(id);
        return "redirect:/kitchen/dashboard";
    }
}
