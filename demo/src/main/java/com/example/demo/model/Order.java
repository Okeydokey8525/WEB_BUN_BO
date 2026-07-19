package com.example.demo.model;

import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.OrderType;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "table_id")
    private RestaurantTable table;
    
    private String customerName;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal serviceCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderType orderType = OrderType.DINE_IN;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(unique = true, length = 64)
    private String publicToken = UUID.randomUUID().toString();

    private LocalDateTime paidAt;

    @ManyToOne
    @JoinColumn(name = "paid_by")
    private User paidBy;

    @Version
    private Long version;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();
    
    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @PrePersist
    void ensurePublicToken() {
        if (publicToken == null || publicToken.isBlank()) {
            publicToken = UUID.randomUUID().toString();
        }
    }
}
