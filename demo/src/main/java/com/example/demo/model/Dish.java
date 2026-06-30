package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    
    @Column(nullable = false)
    private Double price;
    
    private String imageUrl;
    
    @Column(nullable = false)
    private String category; // e.g. "Bún Bò", "Nước uống", "Món thêm"
    
    @Column(nullable = false)
    private boolean isAvailable = true;
    
    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;
}
