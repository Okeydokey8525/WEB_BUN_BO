package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restaurant_tables", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tableNumber", "branch_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String tableNumber; // e.g. "Bàn 1", "Bàn 2"
    
    @Column(nullable = false)
    private String status = "FREE"; // "FREE", "OCCUPIED", "ORDERING"
    
    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;
}
