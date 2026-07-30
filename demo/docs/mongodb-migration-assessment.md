# MongoDB migration assessment

## Recommendation

**No-go for a wholesale migration now.** The application has a transactional relational core (orders, payments/refunds, inventory ledger and shifts) protected by JPA transactions, foreign keys, Flyway migrations and 203 regression tests. Keep PostgreSQL/JPA/Flyway as the system of record while introducing MongoDB only through a separate, versioned migration programme.

## Current persistence inventory

The JPA model includes `Branch`, `Role`, `User`, `Dish`, `RestaurantTable`, `Order`, `OrderItem`, `PaymentTransaction`, `InventoryItem`, `InventoryTransaction`, `Recipe`, `RecipeItem`, `WorkShift` and `ActivityLog`. Repositories use Spring Data JPA with branch-scoped methods and JPQL aggregate queries. Flyway V1–V9 owns relational schema evolution.

Critical relational dependencies include order-to-items/payment/table, recipe-to-dish/inventory item, inventory transaction-to-item/branch/reference, payment transaction-to-order/shift/branch, and work shift-to-cashier/branch. Reporting aggregates payment, orders, inventory ledger and shifts. Services use transactions for payment/refund, stock consume/reversal, order transition and shift close reconciliation.

## Proposed Mongo collections

| Collection | Design decision |
| --- | --- |
| `branches`, `roles`, `users` | Reference-oriented; defer migration because Spring Security depends on the current model. |
| `menuItems` | Document per dish, reference `branchId`; keep image path only. |
| `tables` | Document per restaurant table, reference `branchId`. |
| `orders` | Embed immutable item snapshots (`menuItemId`, name, price, quantity, note); reference branch/table/customer. |
| `payments` | Separate append-only collection, reference order, branch and shift; preserve transaction idempotency key/index. |
| `ingredients` | Document per inventory item and branch. |
| `recipes` | Embed ingredient lines containing `ingredientId`, name snapshot, quantity and unit; reference menu item/branch. |
| `inventoryTransactions` | Separate append-only ledger; reference inventory item, branch and order/reference. |
| `shifts` | Separate document, reference cashier and branch; keep reconciliation snapshot values. |
| `auditLogs` | Separate append-only collection with flexible `metadata` document; strongest early Mongo candidate. |

## Embedding and consistency

Order and recipe lines should be embedded because their historical snapshots must remain valid after menu/ingredient changes. Payments, inventory transactions, shifts and audit records should remain separate documents for append-only history, access filtering and aggregation. Branch IDs must remain present and indexed on every branch-owned document.

Suggested indexes include `{ branchId, createdAt }` for orders/payments/audit, `{ branchId, status }` for tables/orders/shifts, `{ orderId, transactionType, status }` for payments, `{ inventoryItemId, createdAt }` for inventory ledger and uniqueness for idempotency/reference keys.

## Transaction and reporting risks

Payment/refund, stock consume/reversal, shift reconciliation and order status transitions modify multiple records. MongoDB multi-document transactions require a replica set; a standalone local MongoDB instance is not equivalent. Any implementation must retain idempotency constraints, branch filtering and rollback tests.

Revenue, daily revenue, payment-method, top-dish, inventory-consumption and shift reports can use aggregation pipelines, but must be compared against SQL totals during a dual-read/dual-write validation period. Reporting read models are a safer early target than the transactional core.

## Migration phases

1. **M1 – connectivity:** add Spring Data MongoDB under a `mongodb` profile, leave JPA/Flyway untouched, and add Mongo health/integration tests.
2. **M2 – audit:** dual-write or backfill structured audit logs while retaining the existing API contract; validate counts and branch scope.
3. **M3 – reporting read model:** build Mongo projections/aggregation pipelines and compare totals with SQL reporting.
4. **M4 – recipes and inventory:** migrate with ledger/idempotency tests before considering orders, payments and shifts.
5. **M5 – transactional core:** only consider after a replica-set deployment, full parity tests, backup/restore rehearsal and a rollback plan.

## Data migration and rollback

Use a one-way, non-destructive migration tool that reads SQL, preserves the SQL primary key as `legacyId`, writes deterministic documents, and validates record counts, money totals, payment/order links and inventory balances. Keep PostgreSQL authoritative until acceptance criteria pass. Rollback means disabling Mongo reads/writes via configuration and returning to SQL; never delete production SQL data as part of migration.

## Impact and test strategy

Expected impact: entity mappings, repositories, services, `Security` user lookup, reporting aggregates, Flyway/data bootstrap and integration tests. Preserve routes, DTOs, Thymeleaf templates, roles and branch-scoping behavior. Add contract tests for APIs, transaction failure tests, aggregation parity tests, migration count/total checks and Mongo replica-set integration tests.
