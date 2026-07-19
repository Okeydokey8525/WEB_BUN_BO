package com.example.demo.controller;

import com.example.demo.model.Dish;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.RestaurantTable;
import com.example.demo.repository.DishRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.RestaurantTableRepository;
import com.example.demo.model.Branch;
import com.example.demo.repository.BranchRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CustomerController {

    private final DishRepository dishRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final ObjectMapper objectMapper;

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
                         @RequestParam(value = "maxPrice", required = false) Double maxPrice,
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
            @RequestParam("tableId") Long tableId,
            @RequestParam("customerName") String customerName,
            @RequestParam("paymentMethod") String paymentMethod,
            @RequestParam("cartJson") String cartJson,
            Model model) {
        
        try {
            RestaurantTable table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid table ID"));
            
            Order order = new Order();
            order.setTable(table);
            order.setBranch(table.getBranch());
            order.setCustomerName(customerName);
            order.setPaymentMethod(paymentMethod);
            order.setStatus("PENDING");
            order.setPaymentStatus("UNPAID");
            order.setCreatedAt(LocalDateTime.now());
            
            // Set table status to occupied/ordering
            table.setStatus("ORDERING");
            tableRepository.save(table);

            // Parse cart JSON (e.g. [{"dishId": 1, "quantity": 2}, ...])
            List<Map<String, Object>> cartItems = objectMapper.readValue(cartJson, new TypeReference<List<Map<String, Object>>>() {});
            
            double totalAmount = 0.0;
            List<OrderItem> orderItems = new ArrayList<>();
            
            for (Map<String, Object> item : cartItems) {
                Long dishId = Long.valueOf(item.get("dishId").toString());
                int quantity = Integer.parseInt(item.get("quantity").toString());
                
                Dish dish = dishRepository.findById(dishId)
                        .orElseThrow(() -> new IllegalArgumentException("Dish not found: " + dishId));
                
                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setDish(dish);
                orderItem.setQuantity(quantity);
                orderItem.setPrice(dish.getPrice());
                
                totalAmount += dish.getPrice() * quantity;
                orderItems.add(orderItem);
            }
            
            order.setTotalAmount(totalAmount);
            order.setOrderItems(orderItems);
            
            Order savedOrder = orderRepository.save(order);
            return "redirect:/order/status/" + savedOrder.getId();
            
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/menu?error=true";
        }
    }

    @GetMapping("/order/status/{id}")
    public String orderStatus(@PathVariable("id") Long id, Model model) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        
        model.addAttribute("order", order);
        
        // Generate VietQR Image URL if paying via QR
        if ("VIETQR".equals(order.getPaymentMethod()) && "UNPAID".equals(order.getPaymentStatus())) {
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
