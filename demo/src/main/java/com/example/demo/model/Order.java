package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    
    @Column(nullable = false)
    private Double totalAmount = 0.0;
    
    @Column(nullable = false)
    private String status = "PENDING"; // "PENDING", "COOKING", "SERVED", "COMPLETED", "CANCELLED"
    
    @Column(nullable = false)
    private String paymentMethod = "CASH"; // "CASH", "VIETQR"
    
    @Column(nullable = false)
    private String paymentStatus = "UNPAID"; // "UNPAID", "PAID"
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();
    
    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;
}
