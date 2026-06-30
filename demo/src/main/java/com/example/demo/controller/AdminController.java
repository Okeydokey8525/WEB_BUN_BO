package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrderRepository orderRepository;
    private final RestaurantTableRepository tableRepository;
    private final InventoryRepository inventoryRepository;
    private final DishRepository dishRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    private Branch getUserBranch(Authentication authentication) {
        if (authentication == null) return null;
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return null;
        if (user.getBranch() == null) {
            // Global admin, default to first branch
            List<Branch> branches = branchRepository.findAll();
            return branches.isEmpty() ? null : branches.get(0);
        }
        return user.getBranch();
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        Branch branch = getUserBranch(authentication);
        if (branch == null) {
            return "redirect:/login";
        }

        List<Order> allOrders = orderRepository.findByBranchIdOrderByCreatedAtDesc(branch.getId());
        
        // Calculate statistics for today (simulation uses current local date)
        double todayRevenue = 0.0;
        long todayOrderCount = 0;
        long activeOrderCount = 0;
        
        for (Order o : allOrders) {
            // Check if order was created today
            if (o.getCreatedAt().toLocalDate().equals(LocalDate.now())) {
                todayOrderCount++;
                if ("COMPLETED".equals(o.getStatus()) || "PAID".equals(o.getPaymentStatus())) {
                    todayRevenue += o.getTotalAmount();
                }
            }
            if (!"COMPLETED".equals(o.getStatus()) && !"CANCELLED".equals(o.getStatus())) {
                activeOrderCount++;
            }
        }

        // Fetch tables and low-stock alerts
        List<RestaurantTable> tables = tableRepository.findByBranchId(branch.getId());
        List<InventoryItem> lowStockItems = inventoryRepository.findLowStockItemsByBranch(branch.getId());

        model.addAttribute("orders", allOrders);
        model.addAttribute("tables", tables);
        model.addAttribute("lowStockItems", lowStockItems);
        model.addAttribute("todayRevenue", todayRevenue);
        model.addAttribute("todayOrderCount", todayOrderCount);
        model.addAttribute("activeOrderCount", activeOrderCount);
        model.addAttribute("currentBranch", branch);
        
        return "admin/dashboard";
    }

    @PostMapping("/order/{id}/update-status")
    public String updateOrderStatus(@PathVariable("id") Long id, @RequestParam("status") String status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid order ID"));
        
        order.setStatus(status);
        
        // If order is completed or cancelled, free up the table
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            RestaurantTable table = order.getTable();
            if (table != null) {
                table.setStatus("FREE");
                tableRepository.save(table);
            }
            // Auto mark completed orders as PAID
            if ("COMPLETED".equals(status)) {
                order.setPaymentStatus("PAID");
            }
        } else if ("COOKING".equals(status) || "SERVED".equals(status)) {
            RestaurantTable table = order.getTable();
            if (table != null) {
                table.setStatus("OCCUPIED");
                tableRepository.save(table);
            }
        }
        
        orderRepository.save(order);
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/order/{id}/mark-paid")
    public String markOrderAsPaid(@PathVariable("id") Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid order ID"));
        
        order.setPaymentStatus("PAID");
        orderRepository.save(order);
        
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/order/{id}/print")
    public String printInvoice(@PathVariable("id") Long id, Model model) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid order ID"));
        model.addAttribute("order", order);
        return "admin/print";
    }

    // --- Inventory Management ---
    @GetMapping("/inventory")
    public String inventory(Authentication authentication, Model model) {
        Branch branch = getUserBranch(authentication);
        if (branch == null) {
            return "redirect:/login";
        }

        List<InventoryItem> items = inventoryRepository.findByBranchId(branch.getId());
        List<InventoryItem> lowStockItems = inventoryRepository.findLowStockItemsByBranch(branch.getId());
        
        model.addAttribute("inventoryItems", items);
        model.addAttribute("lowStockCount", lowStockItems.size());
        model.addAttribute("currentBranch", branch);
        return "admin/inventory";
    }

    @PostMapping("/inventory/update")
    public String updateInventory(
            @RequestParam("itemId") Long itemId,
            @RequestParam("quantity") Double quantity) {
        
        InventoryItem item = inventoryRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid inventory item ID"));
        
        item.setQuantity(quantity);
        inventoryRepository.save(item);
        
        return "redirect:/admin/inventory";
    }

    // --- Menu CRUD ---
    @GetMapping("/menu")
    public String manageMenu(Authentication authentication, Model model) {
        Branch branch = getUserBranch(authentication);
        if (branch == null) {
            return "redirect:/login";
        }

        model.addAttribute("dishes", dishRepository.findByBranchId(branch.getId()));
        model.addAttribute("currentBranch", branch);
        return "admin/menu";
    }

    @PostMapping("/menu/toggle/{id}")
    public String toggleDishAvailability(@PathVariable("id") Long id) {
        Dish dish = dishRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid dish ID"));
        
        dish.setAvailable(!dish.isAvailable());
        dishRepository.save(dish);
        
        return "redirect:/admin/menu";
    }

    @PostMapping("/menu/save")
    public String saveDish(
            Authentication authentication,
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam("name") String name,
            @RequestParam("price") Double price,
            @RequestParam("category") String category,
            @RequestParam(value = "imageUrl", required = false) String imageUrl) {
        
        Branch branch = getUserBranch(authentication);
        if (branch == null) {
            return "redirect:/login";
        }

        Dish dish;
        if (id != null) {
            dish = dishRepository.findById(id).orElse(new Dish());
        } else {
            dish = new Dish();
            dish.setBranch(branch);
        }
        
        dish.setName(name);
        dish.setPrice(price);
        dish.setCategory(category);
        if (imageUrl != null && !imageUrl.isBlank()) {
            dish.setImageUrl(imageUrl);
        } else if (dish.getImageUrl() == null) {
            dish.setImageUrl("/images/default-food.jpg");
        }
        dish.setAvailable(true);
        
        dishRepository.save(dish);
        return "redirect:/admin/menu";
    }

    @PostMapping("/menu/delete/{id}")
    public String deleteDish(@PathVariable("id") Long id) {
        dishRepository.deleteById(id);
        return "redirect:/admin/menu";
    }
}
