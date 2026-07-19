package com.example.demo.controller;

import com.example.demo.model.Order;
import com.example.demo.service.OrderService;
import com.example.demo.service.RestaurantTableService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/cashier")
@RequiredArgsConstructor
public class CashierController {

    private final OrderService orderService;
    private final RestaurantTableService restaurantTableService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Order> unpaidOrders = orderService.unpaidForCurrentBranch();
        model.addAttribute("unpaidOrders", unpaidOrders);
        model.addAttribute("tables", restaurantTableService.listForCurrentBranch());
        return "cashier/dashboard";
    }
}
