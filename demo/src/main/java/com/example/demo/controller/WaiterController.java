package com.example.demo.controller;

import com.example.demo.model.OrderItem;
import com.example.demo.service.KitchenService;
import com.example.demo.service.RestaurantTableService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/waiter")
@RequiredArgsConstructor
public class WaiterController {

    private final KitchenService kitchenService;
    private final RestaurantTableService restaurantTableService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("tables", restaurantTableService.listForCurrentBranch());
        List<OrderItem> readyItems = kitchenService.readyItemsForCurrentBranch();
        model.addAttribute("readyItems", readyItems);
        return "waiter/dashboard";
    }

    @PostMapping("/serve/{id}")
    public String markAsServed(@PathVariable Long id) {
        kitchenService.markServed(id);
        return "redirect:/waiter/dashboard";
    }
}
