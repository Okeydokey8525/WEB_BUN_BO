package com.example.demo.controller;

import com.example.demo.model.Order;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/cashier")
@RequiredArgsConstructor
public class CashierController {

    private final OrderRepository orderRepository;
    private final RestaurantTableRepository tableRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Find unpaid orders that are not CANCELLED
        List<Order> unpaidOrders = orderRepository.findByPaymentStatusAndStatusNot("UNPAID", "CANCELLED");
        model.addAttribute("unpaidOrders", unpaidOrders);
        model.addAttribute("tables", tableRepository.findAll());
        return "cashier/dashboard";
    }
}
