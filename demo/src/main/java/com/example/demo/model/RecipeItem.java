package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "recipe_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "recipe_id", nullable = false)
    @ToString.Exclude
    private Recipe recipe;
    
    @Column(nullable = false)
    private String ingredientName; // Maps to InventoryItem.ingredientName
    
    @Column(nullable = false)
    private Double amount; // Quantity required for 1 unit of Dish
    
    @Column(nullable = false)
    private String unit; // Unit e.g. "kg", "gam", "lít"
}
