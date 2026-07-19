package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "dishes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal price;
    
    @Column(length = 4000)
    private String imageUrl;
    
    @Column(nullable = false)
    private String category;
    
    @Column(nullable = false)
    private boolean isAvailable = true;

    @Version
    private Long version;
    
    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    public Dish(Long id, String name, BigDecimal price, String imageUrl, String category, boolean isAvailable, Branch branch) {
        this(id, name, price, imageUrl, category, isAvailable, null, branch);
    }

}
