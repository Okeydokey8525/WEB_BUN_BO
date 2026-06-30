package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String action; // e.g. "LOGIN", "CREATE_ORDER", "UPDATE_INVENTORY", "DELETE_MENU"
    
    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
    
    private String ipAddress;
    
    @Column(length = 1000)
    private String description;
}
