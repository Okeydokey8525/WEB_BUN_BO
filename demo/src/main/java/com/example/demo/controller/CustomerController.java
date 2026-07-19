package com.example.demo.controller;

import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.dto.response.CreateOrderResult;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Dish;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.RestaurantTable;
import com.example.demo.repository.DishRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.RestaurantTableRepository;
import com.example.demo.model.Branch;
import com.example.demo.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CustomerController {

    private final DishRepository dishRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final com.example.demo.service.OrderService orderService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featuredDishes", dishRepository.findByCategoryAndIsAvailableTrue("Bún Bò"));
        return "customer/index";
    }


    @GetMapping("/menu")
    public String menu(@RequestParam(value = "tableId", required = false) Long tableId, Model model) {
        Long branchId = null;
        if (tableId != null) {
            RestaurantTable table = tableRepository.findById(tableId).orElse(null);
            if (table != null && table.getBranch() != null) {
                branchId = table.getBranch().getId();
            }
        }
        
        if (branchId == null) {
            List<Branch> branches = branchRepository.findAll();
            if (!branches.isEmpty()) {
                branchId = branches.get(0).getId();
            }
        }

        if (branchId != null) {
            model.addAttribute("dishes", dishRepository.findByBranchIdAndIsAvailableTrue(branchId));
            model.addAttribute("tables", tableRepository.findByBranchId(branchId));
        } else {
            model.addAttribute("dishes", dishRepository.findByIsAvailableTrue());
            model.addAttribute("tables", tableRepository.findAll());
        }
        model.addAttribute("selectedTableId", tableId);
        return "customer/menu";
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "keyword", required = false) String keyword,
                         @RequestParam(value = "category", required = false) String category,
                         @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
                         @RequestParam(value = "sort", required = false) String sort,
                         Model model) {
        List<Dish> allDishes = dishRepository.findByIsAvailableTrue();
        model.addAttribute("dishes", allDishes);
        model.addAttribute("tables", tableRepository.findAll());
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("category", category != null ? category : "All");
        model.addAttribute("maxPrice", maxPrice != null ? maxPrice : 0);
        model.addAttribute("sort", sort != null ? sort : "default");
        return "customer/search";
    }

    @GetMapping("/about")
    public String aboutPage() {
        return "customer/about";
    }

    @PostMapping("/order/place")
    public String placeOrder(
            @Valid @ModelAttribute CreateOrderRequest request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "redirect:/menu?error=validation";
        }
        CreateOrderResult result = orderService.createOrder(request);
        return "redirect:/order/status/" + result.orderId() + "?token=" + result.publicToken();
    }

    @GetMapping("/order/status/{id}")
    public String orderStatus(@PathVariable("id") Long id,
                              @RequestParam(value = "token", required = false) String token,
                              Model model) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("Order not found");
        }
        Order order = orderRepository.findByIdAndPublicToken(id, token)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        model.addAttribute("order", order);
        
        // Generate VietQR Image URL if paying via QR
        if (order.getPaymentMethod() == com.example.demo.model.enums.PaymentMethod.VIETQR && order.getPaymentStatus() == com.example.demo.model.enums.PaymentStatus.UNPAID) {
            String bankId = "ICB"; // Industrial and Commercial Bank of Vietnam (VietinBank)
            String accountNo = "102870123456"; // Mock account number
            String accountName = URLEncoder.encode("BÚN BÒ GIA TRUYỀN", StandardCharsets.UTF_8);
            String amount = String.valueOf(order.getTotalAmount().intValue());
            String desc = URLEncoder.encode("BUNBO_DON_" + order.getId() + "_" + order.getTable().getTableNumber().replace(" ", ""), StandardCharsets.UTF_8);
            
            // Template: qr_only, compact, print, etc. We use compact.
            String qrUrl = String.format("https://img.vietqr.io/image/%s-%s-compact.png?amount=%s&addInfo=%s&accountName=%s",
                    bankId, accountNo, amount, desc, accountName);
            
            model.addAttribute("vietQrUrl", qrUrl);
        }
        
        return "customer/status";
    }
}
