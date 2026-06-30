package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Double quantity = 0.0;
    
    @Column(nullable = false)
    private String unit; // e.g. "kg", "gam", "lít", "cái"
    
    @Column(nullable = false)
    private Double minThreshold = 0.0; // Show warning if quantity < minThreshold
    
    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;
}
