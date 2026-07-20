package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"ingredientName", "branch_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String ingredientName; // e.g. "Thịt nạm bò", "Chả cua", "Bún sợi to", "Nước cốt xương"
    
    @Column(nullable = false)
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;
    
    @Column(nullable = false)
    private String unit; // e.g. "kg", "gam", "lít", "cái"
    
    @Column(nullable = false)
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal minThreshold = BigDecimal.ZERO;
    
    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Version
    private Long version;
}
