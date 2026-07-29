package com.example.demo;

import com.example.demo.dto.request.*;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.model.*;
import com.example.demo.model.enums.*;
import com.example.demo.repository.*;
import com.example.demo.service.InventoryService;
import com.example.demo.service.PaymentService;
import com.example.demo.service.RecipeService;
import com.example.demo.service.ShiftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditWorkflowIntegrationTests {
    @Autowired BranchRepository branches; @Autowired RoleRepository roles; @Autowired UserRepository users;
    @Autowired RestaurantTableRepository tables; @Autowired OrderRepository orders; @Autowired DishRepository dishes;
    @Autowired InventoryRepository inventory; @Autowired ActivityLogRepository auditLogs;
    @Autowired ShiftService shifts; @Autowired PaymentService payments; @Autowired InventoryService stock; @Autowired RecipeService recipes;

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void shiftPaymentAndRefundPersistBranchActorEntityAndMetadata() {
        Branch branch = branches.save(branch("Audit payment")); User cashier = users.save(user("audit-cashier", "ROLE_CASHIER", branch));
        Order order = orders.save(order(branch, "100000")); authenticate(cashier);
        Long shiftId = shifts.openShift(new OpenShiftRequest(BigDecimal.ZERO, "open")).shift().shiftId();
        payments.payOrder(new PayOrderRequest(order.getId(), PaymentMethod.CASH, new BigDecimal("100000"), null, null));
        shifts.closeShift(new CloseShiftRequest(shiftId, new BigDecimal("100000"), "close"));
        Long refundShiftId = shifts.openShift(new OpenShiftRequest(BigDecimal.ZERO, "refund")).shift().shiftId();
        payments.refundOrder(new RefundOrderRequest(order.getId(), "customer"));

        List<ActivityLog> logs = auditLogs.findAll();
        assertAudit(logs, "SHIFT_OPEN", "WORK_SHIFT", shiftId, branch, cashier, "openingCash");
        assertAudit(logs, "SHIFT_CLOSE", "WORK_SHIFT", shiftId, branch, cashier, "expectedCash");
        assertAudit(logs, "PAY_ORDER", "PAYMENT_TRANSACTION", null, branch, cashier, "orderId");
        assertAudit(logs, "REFUND_ORDER", "PAYMENT_TRANSACTION", null, branch, cashier, "shiftId");
        assertTrue(logs.stream().anyMatch(log -> "REFUND_ORDER".equals(log.getAction()) && log.getMetadata().contains(refundShiftId.toString())));
    }

    @Test void stockAndRecipeWorkflowsPersistAuditsAndFailuresDoNot() {
        Branch branch = branches.save(branch("Audit inventory")); User admin = users.save(user("audit-admin", "ROLE_ADMIN", branch)); authenticate(admin);
        InventoryItem item = inventory.save(inventoryItem(branch));
        stock.stockIn(item.getId(), new BigDecimal("2.000"), "delivery");
        stock.adjustStock(item.getId(), new BigDecimal("-1.000"), "count");
        Dish dish = dishes.save(dish(branch));
        Recipe recipe = recipes.createOrUpdateRecipe(new CreateRecipeRequest(dish.getId()));
        RecipeItem recipeItem = recipes.addRecipeItem(recipe.getId(), new RecipeItemRequest(item.getId(), new BigDecimal("1.000")));
        recipes.removeRecipeItem(recipe.getId(), recipeItem.getId());
        long beforeFailure = auditLogs.count();
        assertThrows(BusinessValidationException.class, () -> stock.stockIn(item.getId(), BigDecimal.ZERO, "bad"));
        assertEquals(beforeFailure, auditLogs.count());

        List<ActivityLog> logs = auditLogs.findAll();
        assertAudit(logs, "STOCK_IN", "INVENTORY_TRANSACTION", null, branch, admin, "beforeQuantity");
        assertAudit(logs, "STOCK_ADJUST", "INVENTORY_TRANSACTION", null, branch, admin, "afterQuantity");
        assertAudit(logs, "RECIPE_CREATED", "RECIPE", recipe.getId(), branch, admin, "dishId");
        assertAudit(logs, "RECIPE_ITEM_ADDED", "RECIPE_ITEM", recipeItem.getId(), branch, admin, "inventoryItemId");
        assertAudit(logs, "RECIPE_ITEM_REMOVED", "RECIPE_ITEM", recipeItem.getId(), branch, admin, "recipeId");
    }

    private void assertAudit(List<ActivityLog> logs, String action, String type, Long id, Branch branch, User actor, String metadataKey) {
        ActivityLog log = logs.stream().filter(value -> action.equals(value.getAction()) && type.equals(value.getEntityType()) && (id == null || id.equals(value.getEntityId()))).findFirst().orElseThrow();
        assertEquals(branch.getId(), log.getBranch().getId()); assertEquals(actor.getUsername(), log.getUsername()); assertNotNull(log.getTimestamp()); assertNotNull(log.getMetadata()); assertTrue(log.getMetadata().contains(metadataKey));
    }
    private void authenticate(User user) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), "N/A", List.of(new SimpleGrantedAuthority(user.getRole().getName())))); }
    private Branch branch(String name) { Branch b=new Branch(); b.setName(name); b.setAddress(name); b.setPhone("0"); b.setStatus("ACTIVE"); return b; }
    private User user(String username,String roleName,Branch branch){ Role r=roles.findByName(roleName).orElseGet(()->roles.save(new Role(null,roleName))); User u=new User();u.setUsername(username);u.setPassword("p");u.setRole(r);u.setBranch(branch);u.setEnabled(true);return u; }
    private InventoryItem inventoryItem(Branch b){InventoryItem i=new InventoryItem();i.setIngredientName("Rice");i.setQuantity(new BigDecimal("10.000"));i.setMinThreshold(BigDecimal.ZERO);i.setUnit("kg");i.setBranch(b);return i;}
    private Dish dish(Branch b){Dish d=new Dish();d.setName("Dish-"+System.nanoTime());d.setCategory("Food");d.setPrice(new BigDecimal("100000"));d.setAvailable(true);d.setImageUrl("https://test/image");d.setBranch(b);return d;}
    private Order order(Branch b,String total){RestaurantTable t=new RestaurantTable();t.setTableNumber("T"+System.nanoTime());t.setBranch(b);t.setStatus(TableStatus.FREE);t=tables.save(t);Order o=new Order();o.setBranch(b);o.setTable(t);o.setStatus(OrderStatus.PENDING);o.setPaymentStatus(PaymentStatus.UNPAID);o.setPaymentMethod(PaymentMethod.CASH);o.setOrderType(OrderType.DINE_IN);o.setSubtotal(new BigDecimal(total));o.setTotalAmount(new BigDecimal(total));o.setCreatedAt(LocalDateTime.now());return o;}
}
