# WEB_BUN_BO

Ứng dụng quản lý và bán hàng cho quán bún bò, xây dựng dưới dạng **modular monolith** với Spring Boot, Spring Security, Spring Data JPA, Thymeleaf và Flyway. Mã ứng dụng nằm trong thư mục [`demo`](demo).

## Trạng thái kỹ thuật hiện tại

| Thành phần | Cấu hình hiện tại |
| --- | --- |
| Java | 17 (xác định bởi `demo/pom.xml`) |
| Spring Boot | 4.1.0 |
| Database development/test | H2 in-memory, chạy Flyway migrations |
| Database production | PostgreSQL qua biến môi trường |
| UI | Thymeleaf và CSS thuần |

H2 chỉ phục vụ development và test. Profile `prod` không có thông tin đăng nhập database mặc định: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` là bắt buộc khi triển khai.

## Profiles

- `dev` (mặc định): H2 in-memory, Flyway, SQL logging và H2 Console ở `/h2-console`. Console chỉ lắng nghe local.
- `test`: H2 in-memory cô lập (`bunbo_test`), Flyway, không H2 Console và không seed Java.
- `prod`: PostgreSQL, `ddl-auto=validate`, Flyway bật, SQL logging tắt và upload directory lấy từ `APP_UPLOAD_DIR`.

## Chạy local

Yêu cầu Java 17+ và Maven Wrapper có thể tải Maven, hoặc Maven đã cài sẵn.

```bash
cd demo
./mvnw clean test
./mvnw spring-boot:run
```

Trên Windows, dùng `mvnw.cmd` thay cho `./mvnw`.

Các địa chỉ local:

- Trang khách hàng: `http://localhost:8080/`
- Menu theo bàn: `http://localhost:8080/menu?tableId=3`
- Quản trị: `http://localhost:8080/admin/dashboard`
- H2 Console (chỉ dev): `http://localhost:8080/h2-console`

Flyway tạo dữ liệu demo. Tài khoản demo hiện có mật khẩu `admin123`: `admin`, `cashier`, `waiter`, `kitchen`, và `inventory`. Chỉ sử dụng chúng trên môi trường development.

## Production

1. Sao chép `.env.example` vào hệ thống quản lý biến môi trường của môi trường triển khai; không commit file `.env` chứa dữ liệu thật.
2. Cấu hình PostgreSQL và các biến `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_UPLOAD_DIR`.
3. Khởi chạy với `SPRING_PROFILES_ACTIVE=prod`.

## Flyway

Schema hiện được quản lý trong [`demo/src/main/resources/db/migration`](demo/src/main/resources/db/migration). Không dùng `ddl-auto=create` hoặc `create-drop`; mọi thay đổi schema mới phải được thêm bằng migration Flyway.

## Audit Milestone 1 (2026-07-18)

Đã xác minh rằng tài liệu cũ sai lệch về Java (nêu Java 25 trong khi build đặt Java 17), đường dẫn máy cá nhân, và Microsoft SQL Server. Cấu hình production hiện ưu tiên PostgreSQL và không còn kéo driver SQL Server không được sử dụng.

Các hạng mục phát hiện để xử lý ở milestone tiếp theo:

- Nhiều thao tác quản trị truy vấn tài nguyên bằng `findById` mà chưa kiểm tra branch, tạo nguy cơ IDOR/xuyên chi nhánh.
- Controller đang chứa nghiệp vụ, giá trị tiền dùng `Double`, và trạng thái domain là chuỗi tự do.
- CSRF đang bị bỏ qua quá rộng cho các endpoint thay đổi dữ liệu; các form cần được chuẩn hóa token khi siết Security.
- Luồng đặt món nhận `cartJson` tự do và có thể ghi stack trace; cần DTO, validation, service transaction và xử lý lỗi tập trung.
- Kitchen, waiter, cashier và báo cáo chưa lọc nhất quán theo branch; các test hiện chỉ có context-load.

Milestone 1 chỉ chuẩn hóa tài liệu và cấu hình an toàn tối thiểu, không thay đổi nghiệp vụ hiện hữu. Milestone 2 sẽ ưu tiên branch access control và test bảo mật trước khi refactor domain.

## Milestone 2 — Branch Security (in progress)

### IDOR audit summary

Controller/service audit for `findById`, `deleteById`, `getReferenceById`, and `existsById` found these branch-sensitive risks before the Milestone 2 changes:

| Area | Risk found | Milestone 2 action |
| --- | --- | --- |
| Admin orders/invoices | Order status, mark-paid, and print loaded `Order` by raw ID. | Moved to `OrderService` and require `findByIdAndBranchId`. |
| Admin menu | Toggle/save/delete loaded `Dish` by raw ID; save used `orElse(new Dish())`. | Moved to `DishService`, query by current branch, and reject missing/cross-branch IDs. |
| Admin tables | Save/delete/detail loaded `RestaurantTable` by raw ID. | Moved to `RestaurantTableService`, query by current branch, and block deletion with active orders. |
| Inventory | Quantity update loaded `InventoryItem` by raw ID. | Moved to `InventoryService` with `findByIdAndBranchId`. |
| Kitchen/waiter | Kitchen and waiter dashboards used global item/table queries. | Moved to `KitchenService` and branch-filtered table/order-item queries. |
| Cashier | Cashier dashboard used global unpaid orders/tables. | Moved to `OrderService` and branch-filtered table queries. |
| Public order status | `/order/status/{id}` exposed order status by sequential database ID. | Added `publicToken`; the status endpoint now requires matching ID and token. |

### Authorization decisions

* `ADMIN` is treated as a branch-scoped admin when the account has a branch.
* No existing `ADMIN` account is treated as global just because its branch is `NULL`.
* A future `ROLE_SUPER_ADMIN` can be introduced for explicit global administration, but Milestone 2 does not silently promote any seeded account.
* Branch authorization is centralized in `CurrentUserService` and `BranchAccessService`; controllers should not pick the first branch or trust `branchId` from forms.
* Milestone 2 remains build-unverified in this environment because Maven Central still returns HTTP 403 while resolving the Spring Boot parent POM.

### Security matrix

| Role | Resource | Allowed actions | Branch restriction |
| --- | --- | --- | --- |
| `ROLE_ADMIN` | Orders/invoices | Dashboard, status update, mark paid, print | Current user's branch only |
| `ROLE_ADMIN` | Dishes | List, create, update, toggle, delete | Current user's branch only |
| `ROLE_ADMIN` | Tables | List, detail, create, update, delete when safe | Current user's branch only |
| `ROLE_INVENTORY` | Inventory | List and update quantity | Current user's branch only |
| `ROLE_KITCHEN` | Kitchen items | View pending items and mark ready | Current user's branch only |
| `ROLE_WAITER` | Ready items/tables | View tables and mark served | Current user's branch only |
| `ROLE_CASHIER` | Cashier dashboard | View unpaid orders/tables | Current user's branch only |
| Anonymous customer | Order status | View only with matching `publicToken` | Token-bound public tracking, not branch-login based |

## Milestone 3 — Domain Stabilization (in progress)

### Domain audit summary

| Location | Current type or behavior before Milestone 3 | Risk | Replacement in Milestone 3 |
| --- | --- | --- | --- |
| `Order.status`, `OrderItem.status`, `RestaurantTable.status` | Stored as free-form `String`. | Invalid workflow states could be saved from forms or code paths. | `OrderStatus`, `OrderItemStatus`, and `TableStatus` mapped with `EnumType.STRING`. |
| `Order.paymentStatus`, `Order.paymentMethod` | Stored as free-form `String`. | Payment and serving state could be mixed or invalid. | `PaymentStatus` and `PaymentMethod` enums; completion no longer marks paid automatically. |
| `Dish.price`, `OrderItem.price`, `Order.totalAmount` | Stored/calculated with `Double`/`double`. | Floating-point money errors and unsafe browser-total assumptions. | VND money fields use `BigDecimal` with scale `0`; backend computes subtotal and total. |
| Customer order submit | Controller accepted `cartJson`, parsed maps with `ObjectMapper.readValue`, and calculated total. | Browser could tamper with cart structure, dish IDs, price, or total assumptions. | `CreateOrderRequest` and `CreateOrderItemRequest` with Jakarta Validation; `OrderService.createOrder` loads dish prices from DB. |
| Order and item status update | Transition rules were spread across controller/service string comparisons. | Invalid transitions such as completed back to pending could be saved. | `OrderStateTransitionService` and `OrderItemStateTransitionService`. |
| Error handling | Business paths used generic exceptions and default error behavior. | Users could see unfriendly errors; controller logic was inconsistent. | Global `@ControllerAdvice` maps validation/business/not-found/concurrency errors to safe messages. |

### Money model

For VND, the order money fields use scale `0`:

```text
subtotal = sum(order item lineTotal)
discountAmount = 0 by default
serviceCharge = 0 by default
deliveryFee = 0 by default
taxAmount = 0 by default
totalAmount = subtotal - discountAmount + serviceCharge + deliveryFee + taxAmount
```

`OrderPricingService` rejects negative money components and discounts greater than subtotal. Browser-provided price, subtotal, total, payment status, order status, and branch ID are ignored during order creation.

### Milestone 3 technical debt

* `InventoryItem.quantity`, `InventoryItem.minThreshold`, and `RecipeItem.amount` still use `Double` because they are operational quantities, not money. Inventory unit precision should be revisited in the inventory milestone.
* `Branch.status` remains a `String` because branch lifecycle/soft-delete is scheduled for a later milestone.
* `public_token` is still nullable for historical rows until a cross-database-safe backfill strategy is executed; new application-created orders still generate tokens automatically.
* Maven dependency resolution is still blocked by HTTP 403 in this environment, so Milestone 3 remains not build-verified here.
