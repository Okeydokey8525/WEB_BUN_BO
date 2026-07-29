package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.model.enums.InventoryTransactionType;
import com.example.demo.repository.*;
import com.example.demo.repository.projection.InventoryConsumptionProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryConsumptionReportingRepositoryIntegrationTests {
    @Autowired BranchRepository branchRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired InventoryTransactionRepository inventoryTransactionRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    @Test
    void aggregatesLedgerConsumptionReversalAndWasteWithoutStockAdjustments() {
        Branch branchA = branchRepository.save(branch("Consumption A"));
        Branch branchB = branchRepository.save(branch("Consumption B"));
        User actorA = user(branchA); User actorB = user(branchB);
        InventoryItem rice = item(branchA, "Gao", "kg");
        InventoryItem beef = item(branchA, "Thit bo", "kg");
        InventoryItem other = item(branchB, "Gao", "kg");
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0), to = from.plusDays(1);
        tx(rice, branchA, actorA, InventoryTransactionType.ORDER_CONSUMPTION, "10", from);
        tx(rice, branchA, actorA, InventoryTransactionType.ORDER_CONSUMPTION, "5", from.plusHours(1));
        tx(rice, branchA, actorA, InventoryTransactionType.ORDER_REVERSAL, "3", from.plusHours(2));
        tx(rice, branchA, actorA, InventoryTransactionType.WASTE, "2", from.plusHours(3));
        tx(rice, branchA, actorA, InventoryTransactionType.STOCK_IN, "50", from.plusHours(4));
        tx(rice, branchA, actorA, InventoryTransactionType.ADJUSTMENT_IN, "4", from.plusHours(5));
        tx(rice, branchA, actorA, InventoryTransactionType.ADJUSTMENT_OUT, "1", from.plusHours(6));
        tx(beef, branchA, actorA, InventoryTransactionType.ORDER_CONSUMPTION, "8", from.plusHours(7));
        tx(beef, branchA, actorA, InventoryTransactionType.ORDER_REVERSAL, "1", from.plusHours(8));
        tx(other, branchB, actorB, InventoryTransactionType.ORDER_CONSUMPTION, "100", from.plusHours(9));
        tx(rice, branchA, actorA, InventoryTransactionType.ORDER_CONSUMPTION, "100", to);

        List<InventoryConsumptionProjection> results = inventoryTransactionRepository.aggregateConsumptionByBranchAndCreatedAt(branchA.getId(), from, to, PageRequest.of(0, 10));
        assertEquals(2, results.size());
        assertRow(results.get(0), rice.getId(), "Gao", "kg", "15", "3", "2");
        assertRow(results.get(1), beef.getId(), "Thit bo", "kg", "8", "1", "0");
        assertEquals(1, inventoryTransactionRepository.aggregateConsumptionByBranchAndCreatedAt(branchA.getId(), from, to, PageRequest.of(0, 1)).size());
    }

    private void assertRow(InventoryConsumptionProjection row, Long id, String name, String unit, String consumed, String reversed, String waste) { assertEquals(id, row.getInventoryItemId()); assertEquals(name, row.getInventoryItemName()); assertEquals(unit, row.getUnit()); assertEquals(0, new BigDecimal(consumed).compareTo(row.getConsumedQuantity())); assertEquals(0, new BigDecimal(reversed).compareTo(row.getReversedQuantity())); assertEquals(0, new BigDecimal(waste).compareTo(row.getWasteQuantity())); }
    private Branch branch(String name) { Branch b = new Branch(); b.setName(name); b.setAddress(name); b.setPhone("0"); b.setStatus("ACTIVE"); return b; }
    private InventoryItem item(Branch branch, String name, String unit) { InventoryItem i = new InventoryItem(); i.setIngredientName(name); i.setUnit(unit); i.setQuantity(BigDecimal.ZERO); i.setMinThreshold(BigDecimal.ZERO); i.setBranch(branch); return inventoryRepository.save(i); }
    private User user(Branch branch) { Role role = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> roleRepository.save(new Role(null, "ROLE_ADMIN"))); User u = new User(); u.setUsername("consumption-" + branch.getName()); u.setPassword("password"); u.setRole(role); u.setBranch(branch); u.setEnabled(true); return userRepository.save(u); }
    private void tx(InventoryItem item, Branch branch, User actor, InventoryTransactionType type, String quantity, LocalDateTime time) { InventoryTransaction t = new InventoryTransaction(); t.setInventoryItem(item); t.setBranch(branch); t.setTransactionType(type); t.setQuantity(new BigDecimal(quantity)); t.setQuantityBefore(BigDecimal.ZERO); t.setQuantityAfter(BigDecimal.ZERO); t.setCreatedAt(time); t.setCreatedBy(actor); inventoryTransactionRepository.save(t); }
}
