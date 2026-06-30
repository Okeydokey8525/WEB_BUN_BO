package com.example.demo.config;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(
            BranchRepository branchRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            DishRepository dishRepository,
            RestaurantTableRepository tableRepository,
            InventoryRepository inventoryRepository,
            RecipeRepository recipeRepository) {
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
            Role adminRole, managerRole, staffRole, customerRole;
            if (roleRepository.count() == 0) {
                adminRole = roleRepository.save(new Role(null, "ROLE_ADMIN"));
                managerRole = roleRepository.save(new Role(null, "ROLE_MANAGER"));
                staffRole = roleRepository.save(new Role(null, "ROLE_STAFF"));
                customerRole = roleRepository.save(new Role(null, "ROLE_CUSTOMER"));
                System.out.println("--> Seeded security Roles successfully!");
            } else {
                adminRole = roleRepository.findByName("ROLE_ADMIN").orElse(null);
                managerRole = roleRepository.findByName("ROLE_MANAGER").orElse(null);
                staffRole = roleRepository.findByName("ROLE_STAFF").orElse(null);
                customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElse(null);
            }

            // 3. Seed Users if empty (using BCrypt passwords)
            if (userRepository.count() == 0 && adminRole != null) {
                // BCrypt hash of "admin123" is "$2a$10$dXJ3ADWyyTXmJ.A9.Dk6A.T84KqD1E5i0aB1XpSihHpxfN6PZqA9e"
                userRepository.save(new User(null, "admin", "$2a$10$dXJ3ADWyyTXmJ.A9.Dk6A.T84KqD1E5i0aB1XpSihHpxfN6PZqA9e", "Chủ cửa hàng (Admin)", adminRole, defaultBranch, true));
                userRepository.save(new User(null, "manager", "$2a$10$dXJ3ADWyyTXmJ.A9.Dk6A.T84KqD1E5i0aB1XpSihHpxfN6PZqA9e", "Quản lý cơ sở", managerRole, defaultBranch, true));
                userRepository.save(new User(null, "staff", "$2a$10$dXJ3ADWyyTXmJ.A9.Dk6A.T84KqD1E5i0aB1XpSihHpxfN6PZqA9e", "Nhân viên phục vụ", staffRole, defaultBranch, true));
                System.out.println("--> Seeded default Users (admin, manager, staff) successfully!");
            }

            // 4. Seed Dishes if empty
            Dish bunBoDacBiet = null;
            Dish bunBoTaiNam = null;
            Dish bunBoGioHeo = null;
            
            if (dishRepository.count() == 0) {
                bunBoDacBiet = dishRepository.save(new Dish(null, "Bún Bò Đặc Biệt", 65000.0, "/images/bun-bo-dac-biet.jpg", "Bún Bò", true, defaultBranch));
                bunBoTaiNam = dishRepository.save(new Dish(null, "Bún Bò Tái Nạm Chả", 55000.0, "/images/bun-bo-tai-nam.jpg", "Bún Bò", true, defaultBranch));
                bunBoGioHeo = dishRepository.save(new Dish(null, "Bún Bò Giò Heo", 50000.0, "/images/bun-bo-gio-heo.jpg", "Bún Bò", true, defaultBranch));
                dishRepository.save(new Dish(null, "Chả Cua Thêm (1 viên)", 12000.0, "", "Món thêm", true, defaultBranch));
                dishRepository.save(new Dish(null, "Thịt Nạm Bò Thêm", 18000.0, "", "Món thêm", true, defaultBranch));
                dishRepository.save(new Dish(null, "Trà Đá", 5000.0, "", "Nước uống", true, defaultBranch));
                dishRepository.save(new Dish(null, "Nước Ngọt (Coca/Pepsi)", 15000.0, "", "Nước uống", true, defaultBranch));
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
                tableRepository.save(new RestaurantTable(null, "Bàn 1", "FREE", defaultBranch));
                tableRepository.save(new RestaurantTable(null, "Bàn 2", "FREE", defaultBranch));
                tableRepository.save(new RestaurantTable(null, "Bàn 3", "FREE", defaultBranch));
                tableRepository.save(new RestaurantTable(null, "Bàn 4", "FREE", defaultBranch));
                tableRepository.save(new RestaurantTable(null, "Bàn 5", "FREE", defaultBranch));
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
