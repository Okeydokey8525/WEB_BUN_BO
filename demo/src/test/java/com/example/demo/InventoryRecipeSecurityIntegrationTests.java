package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.model.enums.InventoryTransactionType;
import com.example.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InventoryRecipeSecurityIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired BranchRepository branchRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired DishRepository dishRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired RecipeItemRepository recipeItemRepository;
    @Autowired InventoryTransactionRepository transactionRepository;

    private Branch branchA, branchB;
    private InventoryItem inventoryA, inventoryB;
    private Dish dishA, dishB;

    @BeforeEach
    void setUp() {
        branchA = branchRepository.save(branch("A"));
        branchB = branchRepository.save(branch("B"));
        Role admin = role("ROLE_ADMIN"), inventory = role("ROLE_INVENTORY"), kitchen = role("ROLE_KITCHEN"), user = role("ROLE_USER");
        userRepository.save(appUser("admin-a", admin, branchA));
        userRepository.save(appUser("inventory-a", inventory, branchA));
        userRepository.save(appUser("kitchen-a", kitchen, branchA));
        userRepository.save(appUser("user-a", user, branchA));
        inventoryA = inventoryRepository.save(inventory("stock-a", branchA, "10.000"));
        inventoryB = inventoryRepository.save(inventory("stock-b", branchB, "10.000"));
        dishA = dishRepository.save(dish("dish-a", branchA));
        dishB = dishRepository.save(dish("dish-b", branchB));
    }

    @Test
    void inventoryRoutesRequireInventoryOrAdminAndRespectBranches() throws Exception {
        mockMvc.perform(get("/admin/inventory")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/inventory").with(user("user-a").roles("USER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/inventory").with(user("kitchen-a").roles("KITCHEN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/inventory").with(user("inventory-a").roles("INVENTORY"))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/inventory/{id}", inventoryA.getId()).with(user("admin-a").roles("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/inventory/{id}", inventoryB.getId()).with(user("inventory-a").roles("INVENTORY"))).andExpect(status().isNotFound());
    }

    @Test
    void inventoryPostsRequireCsrfAndRejectInvalidInput() throws Exception {
        mockMvc.perform(post("/admin/inventory/{id}/stock-in", inventoryA.getId())
                        .with(user("inventory-a").roles("INVENTORY")).param("quantity", "1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/inventory/{id}/adjust", inventoryA.getId())
                        .with(user("inventory-a").roles("INVENTORY")).param("quantity", "1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/inventory/{id}/stock-in", inventoryA.getId())
                        .with(user("inventory-a").roles("INVENTORY")).with(csrf()).param("quantity", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/inventory/{id}/stock-in", inventoryA.getId())
                        .with(user("inventory-a").roles("INVENTORY")).with(csrf()).param("quantity", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/inventory/{id}/adjust", inventoryA.getId())
                        .with(user("inventory-a").roles("INVENTORY")).with(csrf())
                        .param("quantity", "1").param("direction", "ADJUSTMENT_IN").param("reason", ""))
                .andExpect(status().isBadRequest());
        assertEquals(new BigDecimal("10.000"), inventoryRepository.findById(inventoryA.getId()).orElseThrow().getQuantity());
    }

    @Test
    void inventoryStockInAndAdjustmentPersistLedger() throws Exception {
        mockMvc.perform(post("/admin/inventory/{id}/stock-in", inventoryA.getId())
                        .with(user("inventory-a").roles("INVENTORY")).with(csrf())
                        .param("quantity", "2.500").param("reason", "delivery"))
                .andExpect(status().is3xxRedirection());
        assertEquals(new BigDecimal("12.500"), inventoryRepository.findById(inventoryA.getId()).orElseThrow().getQuantity());
        InventoryTransaction stockIn = transactions(inventoryA).get(0);
        assertEquals(InventoryTransactionType.STOCK_IN, stockIn.getTransactionType());
        assertEquals(new BigDecimal("10.000"), stockIn.getQuantityBefore());
        assertEquals(new BigDecimal("12.500"), stockIn.getQuantityAfter());

        mockMvc.perform(post("/admin/inventory/{id}/adjust", inventoryA.getId())
                        .with(user("admin-a").roles("ADMIN")).with(csrf())
                        .param("quantity", "1.000").param("direction", "ADJUSTMENT_OUT").param("reason", "count"))
                .andExpect(status().is3xxRedirection());
        assertEquals(new BigDecimal("11.500"), inventoryRepository.findById(inventoryA.getId()).orElseThrow().getQuantity());
        InventoryTransaction adjustment = transactions(inventoryA).get(0);
        assertEquals(InventoryTransactionType.ADJUSTMENT_OUT, adjustment.getTransactionType());
        assertEquals("count", adjustment.getReason());
    }

    @Test
    void recipeRoutesRequireInventoryOrAdmin() throws Exception {
        mockMvc.perform(get("/admin/recipes")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/recipes").with(user("user-a").roles("USER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/recipes").with(user("kitchen-a").roles("KITCHEN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/recipes").with(user("inventory-a").roles("INVENTORY"))).andExpect(status().isOk());
        mockMvc.perform(post("/admin/recipes/dish/{id}", dishA.getId()).with(user("admin-a").roles("ADMIN")).with(csrf())
                        .param("dishId", dishA.getId().toString())).andExpect(status().is3xxRedirection());
    }

    @Test
    void recipePostsEnforceCsrfBranchDuplicateAndQuantityRules() throws Exception {
        mockMvc.perform(post("/admin/recipes/dish/{id}", dishA.getId()).with(user("inventory-a").roles("INVENTORY"))
                        .param("dishId", dishA.getId().toString())).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/recipes/dish/{id}", dishB.getId()).with(user("inventory-a").roles("INVENTORY")).with(csrf())
                        .param("dishId", dishB.getId().toString())).andExpect(status().isNotFound());

        Recipe recipe = recipe(branchA, dishA);
        mockMvc.perform(post("/admin/recipes/{id}/items", recipe.getId()).with(user("inventory-a").roles("INVENTORY")).with(csrf())
                        .param("inventoryItemId", inventoryB.getId().toString()).param("quantityRequired", "1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/recipes/{id}/items", recipe.getId()).with(user("inventory-a").roles("INVENTORY")).with(csrf())
                        .param("inventoryItemId", inventoryA.getId().toString()).param("quantityRequired", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/recipes/{id}/items", recipe.getId()).with(user("inventory-a").roles("INVENTORY")).with(csrf())
                        .param("inventoryItemId", inventoryA.getId().toString()).param("quantityRequired", "1.5"))
                .andExpect(status().is3xxRedirection());
        assertEquals(1, recipeItemRepository.findByRecipeIdAndRecipeBranchId(recipe.getId(), branchA.getId()).size());
        mockMvc.perform(post("/admin/recipes/{id}/items", recipe.getId()).with(user("inventory-a").roles("INVENTORY")).with(csrf())
                        .param("inventoryItemId", inventoryA.getId().toString()).param("quantityRequired", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossBranchRecipeItemDeleteIsRejected() throws Exception {
        Recipe recipeB = recipe(branchB, dishB);
        RecipeItem item = new RecipeItem();
        item.setRecipe(recipeB); item.setInventoryItem(inventoryB); item.setIngredientName(inventoryB.getIngredientName());
        item.setUnit("kg"); item.setAmount(BigDecimal.ONE);
        item = recipeItemRepository.save(item);
        mockMvc.perform(post("/admin/recipes/{recipeId}/items/{itemId}/delete", recipeB.getId(), item.getId())
                        .with(user("inventory-a").roles("INVENTORY")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private List<InventoryTransaction> transactions(InventoryItem item) {
        return transactionRepository.findByInventoryItemIdAndBranchIdOrderByCreatedAtDesc(item.getId(), item.getBranch().getId());
    }
    private Recipe recipe(Branch branch, Dish dish) { Recipe recipe = new Recipe(); recipe.setBranch(branch); recipe.setDish(dish); return recipeRepository.save(recipe); }
    private Branch branch(String name) { Branch b = new Branch(); b.setName(name); b.setAddress(name); b.setPhone("0"); b.setStatus("ACTIVE"); return b; }
    private Role role(String name) { return roleRepository.findByName(name).orElseGet(() -> roleRepository.save(new Role(null, name))); }
    private User appUser(String name, Role role, Branch branch) { User u = new User(); u.setUsername(name); u.setPassword("x"); u.setFullName(name); u.setRole(role); u.setBranch(branch); u.setEnabled(true); return u; }
    private InventoryItem inventory(String name, Branch branch, String quantity) { InventoryItem i = new InventoryItem(); i.setIngredientName(name); i.setBranch(branch); i.setQuantity(new BigDecimal(quantity)); i.setMinThreshold(BigDecimal.ZERO); i.setUnit("kg"); return i; }
    private Dish dish(String name, Branch branch) { Dish d = new Dish(); d.setName(name); d.setBranch(branch); d.setPrice(new BigDecimal("10000")); d.setCategory("Food"); d.setImageUrl("https://example.test/" + name); d.setAvailable(true); return d; }
}
