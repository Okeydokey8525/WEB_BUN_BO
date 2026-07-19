package com.example.demo.model;

import com.example.demo.model.enums.TableStatus;
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
    private String tableNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TableStatus status = TableStatus.FREE;

    @Version
    private Long version;
    
    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;
}
