package com.example.demo.controller;

import com.example.demo.dto.request.PayOrderRequest;
import com.example.demo.dto.request.RefundOrderRequest;
import com.example.demo.model.Order;
import com.example.demo.service.OrderService;
import com.example.demo.service.PaymentService;
import com.example.demo.service.RestaurantTableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/cashier")
@RequiredArgsConstructor
public class CashierController {

    private final OrderService orderService;
    private final RestaurantTableService restaurantTableService;
    private final PaymentService paymentService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Order> unpaidOrders = orderService.unpaidForCurrentBranch();
        model.addAttribute("unpaidOrders", unpaidOrders);
        model.addAttribute("tables", restaurantTableService.listForCurrentBranch());
        return "cashier/dashboard";
    }

    @GetMapping("/orders/{orderId}")
    public String orderDetail(@PathVariable Long orderId, Model model) {
        model.addAttribute("order", orderService.requireOrderForCurrentBranch(orderId));
        model.addAttribute("transactions", paymentService.getTransactionsForOrder(orderId));
        return "cashier/order-detail";
    }

    @PostMapping("/orders/{orderId}/pay")
    public String pay(@PathVariable Long orderId, @Valid @ModelAttribute PayOrderRequest request,
                      RedirectAttributes redirectAttributes) {
        paymentService.payOrder(new PayOrderRequest(orderId, request.paymentMethod(), request.amountTendered(), request.referenceCode(), request.note()));
        redirectAttributes.addFlashAttribute("successMessage", "Thanh toán thành công.");
        return "redirect:/cashier/orders/" + orderId;
    }

    @PostMapping("/orders/{orderId}/refund")
    public String refund(@PathVariable Long orderId, @Valid @ModelAttribute RefundOrderRequest request,
                         RedirectAttributes redirectAttributes) {
        paymentService.refundOrder(new RefundOrderRequest(orderId, request.note()));
        redirectAttributes.addFlashAttribute("successMessage", "Hoàn tiền thành công.");
        return "redirect:/cashier/orders/" + orderId;
    }

    @GetMapping("/transactions")
    public String transactions(Model model) {
        model.addAttribute("transactions", paymentService.getBranchTransactions());
        return "cashier/transactions";
    }
}
