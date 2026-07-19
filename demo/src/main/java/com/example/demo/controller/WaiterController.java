package com.example.demo.controller;

import com.example.demo.model.OrderItem;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/waiter")
@RequiredArgsConstructor
public class WaiterController {

    private final OrderItemRepository orderItemRepository;
    private final RestaurantTableRepository tableRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Show all tables and ready items
        model.addAttribute("tables", tableRepository.findAll());
        List<OrderItem> readyItems = orderItemRepository.findByStatus("READY");
        model.addAttribute("readyItems", readyItems);
        return "waiter/dashboard";
    }

    @PostMapping("/serve/{id}")
    public String markAsServed(@PathVariable Long id) {
        orderItemRepository.findById(id).ifPresent(item -> {
            item.setStatus("SERVED");
            orderItemRepository.save(item);
        });
        return "redirect:/waiter/dashboard";
    }
}
