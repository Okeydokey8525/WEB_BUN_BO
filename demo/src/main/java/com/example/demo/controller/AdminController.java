package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.PaymentStatus;
import com.example.demo.model.enums.TableStatus;
import com.example.demo.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    private final OrderService orderService;
    private final DishService dishService;
    private final RestaurantTableService restaurantTableService;
    private final InventoryService inventoryService;

    private Branch getUserBranch(Authentication authentication) {
        if (authentication == null) return null;
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getBranch() == null) return null;
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
        BigDecimal todayRevenue = BigDecimal.ZERO;
        long todayOrderCount = 0;
        long activeOrderCount = 0;
        
        for (Order o : allOrders) {
            // Check if order was created today
            if (o.getCreatedAt().toLocalDate().equals(LocalDate.now())) {
                todayOrderCount++;
                if (o.getStatus() == OrderStatus.COMPLETED || o.getPaymentStatus() == PaymentStatus.PAID) {
                    todayRevenue = todayRevenue.add(o.getTotalAmount());
                }
            }
            if (o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED) {
                activeOrderCount++;
            }
        }

        // Fetch tables and low-stock alerts
        List<RestaurantTable> tables = restaurantTableService.listForCurrentBranch();
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
    public String updateOrderStatus(@PathVariable("id") Long id, @RequestParam("status") OrderStatus status, @RequestHeader(value = "Referer", required = false) String referer) {
        orderService.updateStatus(id, status);
        return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/admin/dashboard");
    }

    @PostMapping("/order/{id}/mark-paid")
    public String markOrderAsPaid(@PathVariable("id") Long id, @RequestHeader(value = "Referer", required = false) String referer) {
        orderService.markPaid(id);
        
        return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/admin/dashboard");
    }

    @GetMapping("/order/{id}/print")
    public String printInvoice(@PathVariable("id") Long id, Model model) {
        Order order = orderService.requireOrderForCurrentBranch(id);
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

        List<InventoryItem> items = inventoryService.listForCurrentBranch();
        List<InventoryItem> lowStockItems = inventoryService.lowStockForCurrentBranch();
        
        model.addAttribute("inventoryItems", items);
        model.addAttribute("lowStockCount", lowStockItems.size());
        model.addAttribute("currentBranch", branch);
        return "admin/inventory";
    }

    @PostMapping("/inventory/update")
    public String updateInventory(
            @RequestParam("itemId") Long itemId,
            @RequestParam("quantity") Double quantity) {
        
        inventoryService.updateQuantity(itemId, quantity);
        
        return "redirect:/admin/inventory";
    }

    // --- Menu CRUD ---
    @GetMapping("/menu")
    public String manageMenu(Authentication authentication, Model model) {
        Branch branch = getUserBranch(authentication);
        if (branch == null) {
            return "redirect:/login";
        }

        model.addAttribute("dishes", dishService.listForCurrentBranch());
        model.addAttribute("currentBranch", branch);
        return "admin/menu";
    }

    @PostMapping("/menu/toggle/{id}")
    public String toggleDishAvailability(@PathVariable("id") Long id) {
        dishService.toggleAvailability(id);
        
        return "redirect:/admin/menu";
    }

    @PostMapping("/menu/save")
    public String saveDish(
            Authentication authentication,
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam("name") String name,
            @RequestParam("price") BigDecimal price,
            @RequestParam("category") String category,
            @RequestParam(value = "imageUrl", required = false) String imageUrl) {
        
        Branch branch = getUserBranch(authentication);
        if (branch == null) {
            return "redirect:/login";
        }

        dishService.save(id, name, price, category, imageUrl);
        return "redirect:/admin/menu";
    }

    @PostMapping("/menu/delete/{id}")
    public String deleteDish(@PathVariable("id") Long id) {
        dishService.delete(id);
        return "redirect:/admin/menu";
    }

    // ==========================================
    // RESTAURANT TABLES MANAGEMENT (QUẢN LÝ BÀN ĂN)
    // ==========================================
    @GetMapping("/tables")
    public String manageTables(Authentication authentication, Model model) {
        Branch branch = getUserBranch(authentication);
        if (branch == null) return "redirect:/login";

        List<RestaurantTable> tables = restaurantTableService.listForCurrentBranch();
        model.addAttribute("tables", tables);
        model.addAttribute("currentBranch", branch);
        model.addAttribute("activeTab", "tables");
        return "admin/tables";
    }

    @PostMapping("/tables/save")
    public String saveTable(@RequestParam(value = "id", required = false) Long id,
                            @RequestParam("tableNumber") String tableNumber,
                            @RequestParam("status") OrderStatus status,
                            Authentication authentication) {
        Branch branch = getUserBranch(authentication);
        if (branch == null) return "redirect:/login";

        restaurantTableService.save(id, tableNumber, status);
        return "redirect:/admin/tables";
    }

    @PostMapping("/tables/delete/{id}")
    public String deleteTable(@PathVariable("id") Long id) {
        restaurantTableService.delete(id);
        return "redirect:/admin/tables";
    }

    // ==========================================
    // TABLE DETAIL PAGE (CHI TIẾT BÀN ĂN)
    // ==========================================
    @GetMapping("/table-detail")
    public String tableDetail(@RequestParam("id") Long id, Model model) {
        RestaurantTable table = restaurantTableService.requireTableForCurrentBranch(id);
        List<Order> activeOrders = orderService.activeOrdersForTable(id);

        model.addAttribute("table", table);
        model.addAttribute("activeOrders", activeOrders);
        model.addAttribute("activeTab", "tables");
        return "admin/table-detail";
    }
}
