package com.example.demo.config;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.demo.model.enums.TableStatus;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(name = "app.db.seed-java", havingValue = "true", matchIfMissing = false)
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(
            BranchRepository branchRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            DishRepository dishRepository,
            RestaurantTableRepository tableRepository,
            InventoryRepository inventoryRepository,
            RecipeRepository recipeRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        return args -> {
            
            // 1. Seed Branch if empty
            Branch defaultBranch;
            if (branchRepository.count() == 0) {
                defaultBranch = new Branch(null, "Chi nhánh 1 (Quận 1)", "123 Đường Số 4, Q.1, TP.HCM", "0987.654.321", "ACTIVE");
                defaultBranch = branchRepository.save(defaultBranch);
                System.out.println("--> Seeded default Branch successfully!");
            } else {
                defaultBranch = branchRepository.findAll().get(0);
            }

            // 2. Seed Roles if empty
            Role adminRole, cashierRole, waiterRole, kitchenRole, inventoryRole, customerRole;
            if (roleRepository.count() == 0) {
                adminRole = roleRepository.save(new Role(null, "ROLE_ADMIN"));
                cashierRole = roleRepository.save(new Role(null, "ROLE_CASHIER"));
                waiterRole = roleRepository.save(new Role(null, "ROLE_WAITER"));
                kitchenRole = roleRepository.save(new Role(null, "ROLE_KITCHEN"));
                inventoryRole = roleRepository.save(new Role(null, "ROLE_INVENTORY"));
                customerRole = roleRepository.save(new Role(null, "ROLE_CUSTOMER"));
                System.out.println("--> Seeded security Roles successfully!");
            } else {
                adminRole = roleRepository.findByName("ROLE_ADMIN").orElse(null);
                cashierRole = roleRepository.findByName("ROLE_CASHIER").orElse(null);
                waiterRole = roleRepository.findByName("ROLE_WAITER").orElse(null);
                kitchenRole = roleRepository.findByName("ROLE_KITCHEN").orElse(null);
                inventoryRole = roleRepository.findByName("ROLE_INVENTORY").orElse(null);
                customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElse(null);
            }

            // 3. Seed Users if empty (using BCrypt passwords)
            if (userRepository.count() == 0 && adminRole != null) {
                String encodedPassword = passwordEncoder.encode("admin123");
                userRepository.save(new User(null, "admin", encodedPassword, "Chủ cửa hàng (Admin)", adminRole, defaultBranch, true));
                userRepository.save(new User(null, "cashier", encodedPassword, "Nhân viên Thu ngân", cashierRole, defaultBranch, true));
                userRepository.save(new User(null, "waiter", encodedPassword, "Nhân viên Phục vụ", waiterRole, defaultBranch, true));
                userRepository.save(new User(null, "kitchen", encodedPassword, "Nhân viên Bếp", kitchenRole, defaultBranch, true));
                userRepository.save(new User(null, "inventory", encodedPassword, "Nhân viên Kho", inventoryRole, defaultBranch, true));
                userRepository.save(new User(null, "customer", encodedPassword, "Khách hàng Mẫu", customerRole, defaultBranch, true));
                System.out.println("--> Seeded default Users (admin, cashier, waiter, kitchen, inventory, customer) successfully!");
            }

            // 4. Seed Dishes if empty
            Dish bunBoDacBiet = null;
            Dish bunBoTaiNam = null;
            Dish bunBoGioHeo = null;
            
            if (dishRepository.count() == 0) {
                bunBoDacBiet = dishRepository.save(new Dish(null, "Bún Bò Đặc Biệt", new BigDecimal("65000"), "https://images.unsplash.com/photo-1555126634-323283e090fa?w=600&auto=format&fit=crop", "Bún Bò", true, defaultBranch));
                bunBoTaiNam = dishRepository.save(new Dish(null, "Bún Bò Tái Nạm Chả", new BigDecimal("55000"), "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=600&auto=format&fit=crop", "Bún Bò", true, defaultBranch));
                bunBoGioHeo = dishRepository.save(new Dish(null, "Bún Bò Giò Heo", new BigDecimal("50000"), "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=600&auto=format&fit=crop", "Bún Bò", true, defaultBranch));
                dishRepository.save(new Dish(null, "Chả Cua Thêm (1 viên)", new BigDecimal("12000"), "https://images.unsplash.com/photo-1541544741938-0af808871cc0?w=600&auto=format&fit=crop", "Món thêm", true, defaultBranch));
                dishRepository.save(new Dish(null, "Thịt Nạm Bò Thêm", new BigDecimal("18000"), "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=600&auto=format&fit=crop", "Món thêm", true, defaultBranch));
                dishRepository.save(new Dish(null, "Trà Đá", new BigDecimal("5000"), "https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=600&auto=format&fit=crop", "Nước uống", true, defaultBranch));
                dishRepository.save(new Dish(null, "Nước Ngọt (Coca/Pepsi)", new BigDecimal("15000"), "https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=600&auto=format&fit=crop", "Nước uống", true, defaultBranch));
                System.out.println("--> Seeded Menu Dishes successfully!");
            } else {
                List<Dish> list = dishRepository.findByBranchId(defaultBranch.getId());
                for (Dish d : list) {
                    if ("Bún Bò Đặc Biệt".equals(d.getName())) bunBoDacBiet = d;
                    if ("Bún Bò Tái Nạm Chả".equals(d.getName())) bunBoTaiNam = d;
                    if ("Bún Bò Giò Heo".equals(d.getName())) bunBoGioHeo = d;
                }
            }

            // 5. Seed Restaurant Tables if empty
            if (tableRepository.count() == 0) {
                tableRepository.save(new RestaurantTable(null, "Bàn 1", TableStatus.FREE, null, defaultBranch));
                tableRepository.save(new RestaurantTable(null, "Bàn 2", TableStatus.FREE, null, defaultBranch));
                tableRepository.save(new RestaurantTable(null, "Bàn 3", TableStatus.FREE, null, defaultBranch));
                tableRepository.save(new RestaurantTable(null, "Bàn 4", TableStatus.FREE, null, defaultBranch));
                tableRepository.save(new RestaurantTable(null, "Bàn 5", TableStatus.FREE, null, defaultBranch));
                System.out.println("--> Seeded Restaurant Tables successfully!");
            }

            // 6. Seed Inventory if empty
            if (inventoryRepository.count() == 0) {
                // High stock
                inventoryRepository.save(new InventoryItem(null, "Bún sợi to", 35.0, "kg", 15.0, defaultBranch));
                inventoryRepository.save(new InventoryItem(null, "Rau sống ăn kèm", 12.0, "kg", 5.0, defaultBranch));
                
                // Low stock (triggers warning)
                inventoryRepository.save(new InventoryItem(null, "Thịt nạm bò", 3.2, "kg", 8.0, defaultBranch));
                inventoryRepository.save(new InventoryItem(null, "Chả cua", 45.0, "viên", 100.0, defaultBranch));
                inventoryRepository.save(new InventoryItem(null, "Nước cốt xương hầm", 8.5, "lít", 20.0, defaultBranch));
                System.out.println("--> Seeded Inventory Items successfully!");
            }

            // 7. Seed Recipes if empty
            if (recipeRepository.count() == 0 && bunBoDacBiet != null && bunBoTaiNam != null && bunBoGioHeo != null) {
                // Recipe for Bún Bò Đặc Biệt
                Recipe rDacBiet = new Recipe(null, bunBoDacBiet, defaultBranch, null);
                rDacBiet.setRecipeItems(Arrays.asList(
                    new RecipeItem(null, rDacBiet, "Thịt nạm bò", 0.15, "kg"),
                    new RecipeItem(null, rDacBiet, "Chả cua", 1.0, "viên"),
                    new RecipeItem(null, rDacBiet, "Bún sợi to", 0.15, "kg"),
                    new RecipeItem(null, rDacBiet, "Nước cốt xương hầm", 0.2, "lít")
                ));
                recipeRepository.save(rDacBiet);

                // Recipe for Bún Bò Tái Nạm Chả
                Recipe rTaiNam = new Recipe(null, bunBoTaiNam, defaultBranch, null);
                rTaiNam.setRecipeItems(Arrays.asList(
                    new RecipeItem(null, rTaiNam, "Thịt nạm bò", 0.10, "kg"),
                    new RecipeItem(null, rTaiNam, "Chả cua", 1.0, "viên"),
                    new RecipeItem(null, rTaiNam, "Bún sợi to", 0.15, "kg"),
                    new RecipeItem(null, rTaiNam, "Nước cốt xương hầm", 0.2, "lít")
                ));
                recipeRepository.save(rTaiNam);

                // Recipe for Bún Bò Giò Heo
                Recipe rGioHeo = new Recipe(null, bunBoGioHeo, defaultBranch, null);
                rGioHeo.setRecipeItems(Arrays.asList(
                    new RecipeItem(null, rGioHeo, "Bún sợi to", 0.15, "kg"),
                    new RecipeItem(null, rGioHeo, "Nước cốt xương hầm", 0.2, "lít")
                ));
                recipeRepository.save(rGioHeo);

                System.out.println("--> Seeded Recipes successfully!");
            }
        };
    }
}
