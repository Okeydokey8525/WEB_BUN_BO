package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import lombok.ToString;
import lombok.EqualsAndHashCode;
import java.util.Set;
import java.util.HashSet;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    private String fullName;
    
    @Column(name = "avatar_url", length = 4000)
    private String avatarUrl;
    
    private String phone;
    
    private String address;
    
    private String email;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch; // Can be null for global admin
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_favorites",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "dish_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Dish> favoriteDishes = new HashSet<>();

    public User(Long id, String username, String password, String fullName, Role role, Branch branch, boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.branch = branch;
        this.enabled = enabled;
    }
}
