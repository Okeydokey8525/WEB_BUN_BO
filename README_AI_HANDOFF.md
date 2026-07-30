# BÁO CÁO BÀN GIAO & TỔNG QUAN HỆ THỐNG DỰ ÁN: WEB_BUN_BO (BÚN BÒ GIA TRUYỀN)
*Tài liệu này được thiết kế dành riêng cho ChatGPT (và các AI Coding Assistant / Developer) nhằm cung cấp bối cảnh kỹ thuật, kiến trúc, nghiệp vụ và lịch sử refactor chi tiết nhất mà không bị lược bỏ.*

---

## 1. TỔNG QUAN DỰ ÁN & MỤC TIÊU NGHIỆP VỤ

### 1.1. Mục đích hệ thống
**`WEB_BUN_BO`** là một hệ thống phần mềm quản lý toàn diện (chuỗi) quán Bún Bò Gia Truyền, được xây dựng theo kiến trúc **Modular Monolith**. Hệ thống giải quyết trọn vẹn vòng đời vận hành của một chuỗi nhà hàng ẩm thực từ khâu:
- **Khách hàng (Customer/Dine-in Guest):** Xem thực đơn (Menu), tìm kiếm món ăn theo danh mục, quét mã QR tại bàn để đặt món (`/menu?tableId=X`), theo dõi trạng thái món ăn theo thời gian thực (qua `publicToken`), và quản lý thông tin cá nhân/món yêu thích.
- **Bếp (Kitchen/Chef):** Nhận thông báo món đặt ngay lập tức, chuyển trạng thái chế biến (`PREPARING` -> `READY`).
- **Phục vụ (Waiter):** Xem các món đã làm xong (`READY`) theo bàn, chuyển trạng thái phục vụ cho khách (`SERVED`), quản lý trạng thái bàn (`FREE`, `OCCUPIED`, `ORDERING`).
- **Thu ngân (Cashier):** Quản lý hóa đơn các bàn đang dùng bữa, kiểm tra chi tiết các món (`OrderItem`), thực hiện thanh toán (`UNPAID` -> `PAID`), ghi nhận phương thức thanh toán (`CASH`, `TRANSFER`, `CARD`).
- **Quản lý kho (Inventory Manager):** Kiểm soát nguyên liệu thực phẩm (thịt bò, bún, nước dùng, hành...), theo dõi định mức cảnh báo hết hàng (`minThreshold`).
- **Quản trị viên chi nhánh / Hệ thống (Admin):** Quản lý thực đơn (Dish), cấu hình bàn ăn (RestaurantTable), xem báo cáo doanh thu, lịch sử hóa đơn, in phiếu thanh toán, và theo dõi nhật ký hoạt động (`ActivityLog`).

---

## 2. CÔNG NGHỆ SỬ DỤNG (TECH STACK & CONFIGURATION)

### 2.1. Nền tảng cốt lõi
- **Ngôn ngữ:** Java 17 LTS (Xác định rõ ràng tại `demo/pom.xml` qua property `<java.version>17</java.version>`). *Lưu ý: Không dùng Java 25 hay Python/Flask như các tài liệu cũ.*
- **Framework:** Spring Boot 4.1.0 (sử dụng cấu trúc package và cơ chế hiện đại của Spring Boot 4.x).
- **ORM / Data Access:** Spring Data JPA + Hibernate (Jakarta Persistence).
- **Giao diện (Frontend):** Server-side rendering với Thymeleaf + Thymeleaf Extras Spring Security 6 + Vanilla CSS (`style.css` với theme màu đỏ gạch truyền thống `#C0392B`, micro-animations, responsive grid).
- **Bảo mật & Xác thực:** Spring Security 6.x (Session/Cookie-based authentication + Form Login + CSRF protection + URL/Method authorization).
- **Công cụ hỗ trợ (Utilities):** Lombok (giảm boilerplate code cho getter, setter, constructor, builder).
- **Build Tool:** Maven (sử dụng Maven Wrapper `mvnw` / `mvnw.cmd`).

### 2.2. Cơ sở dữ liệu & Cấu hình theo Profile (`application-*.yml`)
Hệ thống sử dụng cơ chế quản lý migration nghiêm ngặt với **Flyway (`org.flywaydb`)**, tuyệt đối **không** dùng `spring.jpa.hibernate.ddl-auto=create` hoặc `update` trong môi trường thực tế.

- **Profile `dev` (Mặc định):**
  - Sử dụng database in-memory **H2 Database** (`jdbc:h2:mem:bunbodb`).
  - Flyway tự động chạy migration từ `db/migration/` và tự tạo dữ liệu seed mẫu.
  - Bật H2 Console tại đường dẫn `http://localhost:8080/h2-console` (cho phép cấu hình trong Spring Security frame options).
  - SQL Logging và Hibernate statistics được bật để tiện debug.
- **Profile `test`:**
  - Sử dụng H2 in-memory cô lập (`bunbo_test`), tắt H2 Console và không seed các class Java phụ trợ để đảm bảo tính tinh gọn khi chạy integration/unit test.
- **Profile `prod`:**
  - Cấu hình kết nối **PostgreSQL** thông qua biến môi trường (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
  - `hibernate.ddl-auto=validate` (chỉ kiểm tra khớp schema, mọi thay đổi phải đi qua file migration mới của Flyway).
  - Đường dẫn lưu trữ ảnh tải lên được cấu hình động qua `APP_UPLOAD_DIR`.

---

## 3. CẤU TRÚC THƯ MỤC & PACKAGING

Toàn bộ source code nằm trong thư mục **`demo/`**:
```text
c:\LeDucLuong\HK VII\WEB_BUN_BO\demo\
├── pom.xml                                      <- Cấu hình dependencies (Spring Boot 4.1.0, JPA, Thymeleaf, Flyway, H2, PostgreSQL, Lombok)
├── src/main/java/com/example/demo/
│   ├── DemoApplication.java                     <- Entrypoint Spring Boot application
│   ├── config/
│   │   ├── SecurityConfig.java                  <- Cấu hình phân quyền URL, CSRF, Form Login, Logout
│   │   ├── CustomAuthenticationSuccessHandler.java <- Điều hướng login thông minh theo Role (Admin/Cashier/Waiter/Kitchen/Inventory)
│   │   ├── DataInitializer.java                 <- Seed data bổ sung cho môi trường Dev/Test (nếu cần)
│   │   └── FlywayConfig.java                    <- Cấu hình Flyway migration bridge
│   ├── controller/
│   │   ├── CustomerController.java              <- Xử lý trang chủ (/), thực đơn (/menu), đặt món (/order/place), theo dõi (/order/status/{id})
│   │   ├── AdminController.java                 <- Quản lý POS, Dashboard, Menu CRUD, Table CRUD, In hóa đơn (/admin/**)
│   │   ├── CashierController.java               <- Dashboard thu ngân, quản lý thanh toán (/cashier/**)
│   │   ├── WaiterController.java                <- Dashboard phục vụ, chuyển trạng thái phục vụ món (/waiter/**)
│   │   ├── KitchenController.java               <- Dashboard bếp, chuyển trạng thái chế biến món (/kitchen/**)
│   │   ├── AuthController.java                  <- Đăng nhập, đăng ký, quên mật khẩu
│   │   ├── ProfileController.java               <- Trang cá nhân người dùng, món ăn yêu thích (/profile/**, /api/favorites/**)
│   │   └── GlobalControllerAdvice.java          <- Catcher & Exception Handler tập trung, trả về thông báo lỗi thân thiện cho UI
│   ├── dto/
│   │   ├── request/
│   │   │   ├── CreateOrderRequest.java          <- DTO đặt món từ phía khách (chứa tableId, customerName, orderType, danh sách items)
│   │   │   └── CreateOrderItemRequest.java      <- DTO chi tiết từng món (dishId, quantity, note)
│   │   └── response/
│   │       └── CreateOrderResult.java           <- DTO trả về sau khi đặt món thành công (chứa orderId và publicToken)
│   ├── exception/
│   │   ├── ResourceNotFoundException.java       <- Lỗi không tìm thấy tài nguyên (Dish, Order, Table...)
│   │   ├── BranchAccessDeniedException.java     <- Lỗi truy cập chéo chi nhánh (Cross-branch access violation)
│   │   ├── BusinessValidationException.java     <- Lỗi vi phạm nghiệp vụ chung
│   │   ├── InvalidOrderStateException.java      <- Lỗi trạng thái đơn hàng không hợp lệ khi transition
│   │   ├── InvalidOrderItemStateException.java  <- Lỗi trạng thái món ăn không hợp lệ khi transition
│   │   └── InvalidPaymentStateException.java    <- Lỗi thanh toán không hợp lệ
│   ├── model/
│   │   ├── Branch.java                          <- Chi nhánh quán (id, name, address, phone, status)
│   │   ├── Role.java                            <- Vai trò (ROLE_ADMIN, ROLE_CASHIER, ROLE_WAITER, ROLE_KITCHEN, ROLE_INVENTORY, ROLE_USER)
│   │   ├── User.java                            <- Tài khoản người dùng (gắn với Role và Branch cụ thể)
│   │   ├── Dish.java                            <- Món ăn (name, price BigDecimal, imageUrl, category, isAvailable, branch, version)
│   │   ├── RestaurantTable.java                 <- Bàn ăn (tableNumber, TableStatus, branch, version)
│   │   ├── Order.java                           <- Hóa đơn đặt hàng (table, customerName, subtotal, discount, totalAmount BigDecimal, OrderStatus, PaymentStatus, PaymentMethod, branch, publicToken, version)
│   │   ├── OrderItem.java                       <- Chi tiết món trong hóa đơn (order, dish, quantity, price BigDecimal, lineTotal BigDecimal, OrderItemStatus, dishNameSnapshot, note, version)
│   │   ├── InventoryItem.java                   <- Nguyên liệu trong kho (ingredientName, quantity, unit, minThreshold, branch)
│   │   ├── Recipe.java & RecipeItem.java        <- Công thức định lượng nguyên liệu cho món ăn
│   │   ├── ActivityLog.java                     <- Nhật ký kiểm toán hệ thống (username, action, timestamp, ipAddress, description)
│   │   └── enums/
│   │       ├── OrderStatus.java                 <- PENDING, CONFIRMED, PREPARING, READY, SERVED, COMPLETED, CANCELLED
│   │       ├── OrderItemStatus.java             <- PENDING, PREPARING, READY, SERVED, CANCELLED
│   │       ├── TableStatus.java                 <- FREE, OCCUPIED, ORDERING, RESERVED
│   │       ├── PaymentStatus.java               <- UNPAID, PAID, REFUNDED
│   │       ├── PaymentMethod.java               <- CASH, TRANSFER, CARD
│   │       └── OrderType.java                   <- DINE_IN, TAKE_AWAY, DELIVERY
│   ├── repository/                              <- Các interface Spring Data JPA (DishRepository, OrderRepository, RestaurantTableRepository...)
│   ├── security/
│   │   ├── CurrentUserService.java              <- Lấy thông tin User, Role và Branch ID của người dùng đang đăng nhập (Spring Security Context)
│   │   └── BranchAccessService.java             <- Core Engine kiểm tra quyền truy cập tài nguyên theo chi nhánh (Ngăn chặn IDOR)
│   └── service/                                 <- Tách biệt hoàn toàn nghiệp vụ khỏi Controller
│       ├── DishService.java                     <- Quản lý CRUD thực đơn theo Branch
│       ├── RestaurantTableService.java          <- Quản lý CRUD bàn ăn, kiểm tra bàn trống/đang có khách
│       ├── InventoryService.java                <- Quản lý kho, cảnh báo nguyên liệu sắp hết (`quantity <= minThreshold`)
│       ├── OrderService.java                    <- Tạo đơn hàng mới, tính giá tiền bảo mật, lấy danh sách theo vai trò
│       ├── OrderPricingService.java             <- Logic tính toán tiền tệ (VND - BigDecimal scale 0), chống giả mạo giá từ UI
│       ├── OrderStateTransitionService.java     <- FSM kiểm soát chuyển trạng thái đơn hàng hợp lệ
│       ├── OrderItemStateTransitionService.java <- FSM kiểm soát chuyển trạng thái từng món trong bếp/phục vụ
│       └── KitchenService.java                  <- Truy vấn hàng đợi món ăn cho Bếp và Phục vụ
└── src/main/resources/
    ├── application.yml, application-dev.yml, application-test.yml, application-prod.yml
    ├── db/migration/                            <- Các file Flyway SQL migration (V1 đến V6)
    ├── static/                                  <- CSS (`style.css`), JS, images
    └── templates/                               <- Thymeleaf templates (`fragments/layout.html`, `customer/*`, `admin/*`, `cashier/*`, `waiter/*`, `kitchen/*`, `error.html`)
```

---

## 4. LỊCH SỬ MIGRATION CƠ SỞ DỮ LIỆU (FLYWAY DDL)

Toàn bộ database schema được tiến hóa qua 6 phiên bản migration chuẩn xác:

### `V1__init_schema.sql` — Khởi tạo 11 bảng cốt lõi
- Tạo các bảng: `branches`, `roles`, `users`, `dishes`, `restaurant_tables`, `inventory`, `recipes`, `recipe_items`, `orders`, `order_items`, `activity_logs`.
- Thiết lập khóa ngoại (`REFERENCES`), khóa chính (`BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`).
- Mối quan hệ đa chi nhánh: Cả `users`, `dishes`, `restaurant_tables`, `inventory`, `recipes`, `orders` đều có `branch_id REFERENCES branches(id)`.

### `V2__seed_data.sql` — Nạp dữ liệu mẫu ban đầu
- Tạo chi nhánh: `Chi nhánh 1 - Quận 1 (TP.HCM)` và `Chi nhánh 2 - Quận 3 (TP.HCM)`.
- Tạo các role chuẩn: `ROLE_ADMIN`, `ROLE_CASHIER`, `ROLE_WAITER`, `ROLE_KITCHEN`, `ROLE_INVENTORY`, `ROLE_USER`.
- Tạo tài khoản demo (đều có mật khẩu `admin123` - bcrypt hashed): `admin` (Chi nhánh 1), `cashier`, `waiter`, `kitchen`, `inventory`, `customer`.
- Tạo các món ăn truyền thống bún bò (`Bún bò đặc biệt`, `Bún bò tái nạm`, `Bún bò gân`, `Chả cua thêm`...), bàn ăn (`Bàn 01` -> `Bàn 10`), và nguyên liệu kho.

### `V3__user_profile_and_favorites.sql` — Mở rộng profile & món yêu thích
- Thêm cột cho `users`: `avatar_url`, `phone`, `address`, `email`.
- Tạo bảng `user_favorites (user_id, dish_id, PRIMARY KEY (user_id, dish_id))` với `ON DELETE CASCADE`.

### `V4__expand_url_columns.sql` — Mở rộng độ dài link ảnh
- Thay đổi độ dài `image_url` (bảng `dishes`) và `avatar_url` (bảng `users`) lên `VARCHAR(4000)` để hỗ trợ link CDN dài hoặc base64/data URI.

### `V5__order_public_token.sql` — Bảo mật đường dẫn theo dõi đơn hàng công khai
- Thêm cột `public_token VARCHAR(64)` vào bảng `orders` kèm `UNIQUE INDEX ux_orders_public_token`.
- Thêm các chỉ mục (`INDEX`) quan trọng để tối ưu hiệu năng truy vấn cho dashboard bếp và chi nhánh: `ix_orders_branch_status_created`, `ix_order_items_order_status`.

### `V6__stabilize_order_domain.sql` — Chuẩn hóa nghiệp vụ, Tiền tệ VND & Optimistic Locking
- **Chuẩn hóa tiền tệ sang `NUMERIC(19, 0)`:** Thay đổi `price` (`dishes`, `order_items`) và `total_amount` (`orders`) từ số thực tự do sang `NUMERIC(19, 0)` (VND không lấy số thập phân).
- **Thêm cấu phần chi tiết đơn hàng:** Thêm cột `subtotal`, `discount_amount`, `service_charge`, `delivery_fee`, `tax_amount`, `order_type (DINE_IN/TAKE_AWAY/DELIVERY)`, `paid_at`, `paid_by` vào bảng `orders`.
- **Snapshot tên món ăn và tính sẵn giá dòng:** Thêm `dish_name_snapshot VARCHAR(255) NOT NULL`, `line_total NUMERIC(19, 0)`, `note VARCHAR(255)` vào bảng `order_items`. (Đảm bảo khi đổi tên/giá món trong menu sau này, lịch sử đơn hàng cũ không bị sai lệch).
- **Chuẩn hóa trạng thái lịch sử:** Cập nhật đồng bộ các trạng thái cũ (`PREPARING` thay cho `COOKING`, `CONFIRMED`...).
- **Optimistic Locking:** Thêm cột `version BIGINT DEFAULT 0 NOT NULL` vào các bảng `orders`, `order_items`, `restaurant_tables`, và `dishes` để chống Race Condition (cạnh tranh đồng thời) khi thu ngân, bếp và khách cùng cập nhật đơn/bàn.

---

## 5. KIẾN TRÚC BẢO MẬT & PHÂN QUYỀN (SECURITY MATRIX & BRANCH ACCESS)

### 5.1. Phân chia quyền hạn theo Role (`SecurityConfig.java`)
- **Khách ẩn danh / Công cộng (PermitAll):** `/`, `/menu`, `/about`, `/search`, `/register`, `/forgot-password`, `/order/place`, `/order/status/**`, static assets, `/h2-console/**`.
- **Thu ngân (`ROLE_CASHIER` & `ROLE_ADMIN`):** `/cashier/**`.
- **Phục vụ (`ROLE_WAITER` & `ROLE_ADMIN`):** `/waiter/**`.
- **Bếp (`ROLE_KITCHEN` & `ROLE_ADMIN`):** `/kitchen/**`.
- **Quản lý kho (`ROLE_INVENTORY` & `ROLE_ADMIN`):** `/admin/inventory/**`.
- **Quản trị viên (`ROLE_ADMIN`):** `/admin/**` (POS, cấu hình bàn, thực đơn, in ấn).
- **Khách hàng đã đăng ký (`ROLE_USER` & các role khác):** `/profile/**`, `/api/favorites/**`.

### 5.2. Chống lỗ hổng IDOR & Cách ly dữ liệu chi nhánh (Milestone 2 - Branch Access Engine)
Trước đây, hệ thống gọi `repository.findById(id)` từ Controller, dẫn đến rủi ro **IDOR (Insecure Direct Object Reference)** — một tài khoản ở Chi nhánh 1 có thể truyền ID bàn hoặc đơn hàng của Chi nhánh 2 vào URL và thao tác sai phép.

**Giải pháp hiện tại:**
- **`CurrentUserService`**: Cung cấp hàm `getCurrentBranchId()` và `getCurrentUser()` từ session đăng nhập hiện tại. Nếu user có `branch_id` cụ thể, mọi truy vấn đều bị khóa chặt trong chi nhánh đó.
- **`BranchAccessService`**: Cung cấp các hàm kiểm tra bắt buộc như `requireBranchMatch(branchId, resourceName)` hoặc `validateAccess(branchId)`.
- **Quy tắc cho Role `ADMIN`:** Một tài khoản `ROLE_ADMIN` nếu được gán vào `branch_id = 1` sẽ chỉ hoạt động với tư cách **Admin chi nhánh 1** (nhìn thấy và sửa món/bàn/đơn của chi nhánh 1). Tài khoản admin có `branch_id = NULL` hoặc role hệ thống tương lai mới có khả năng truy xuất toàn cục.
- **Dữ liệu trả về:** Mọi Service (`OrderService`, `DishService`, `RestaurantTableService`, `InventoryService`, `KitchenService`) đều sử dụng các query có điều kiện `AND branch_id = :branchId` (ví dụ: `findByIdAndBranchId`).

---

## 6. CHUẨN HÓA NGHIỆP VỤ & LUỒNG XỬ LÝ (DOMAIN STABILIZATION - MILESTONE 3)

### 6.1. Mô hình Tiền tệ (VND Money Model via `OrderPricingService`)
Để chấm dứt lỗi làm tròn sai của `Double` và rủi ro trình duyệt tự tính tổng tiền sai hoặc giả mạo (`cartJson`), hệ thống áp dụng logic:
- Khi khách đặt món từ trình duyệt qua `CreateOrderRequest`, backend **chỉ nhận ID món và số lượng**. Toàn bộ giá `price` của từng món được tải lên từ cơ sở dữ liệu (bảng `dishes`) tại thời điểm đặt.
- `OrderPricingService` tính toán với `BigDecimal` (scale 0, làm tròn chuẩn VND):
  $$\text{lineTotal} = \text{price} \times \text{quantity}$$
  $$\text{subtotal} = \sum (\text{lineTotal})$$
  $$\text{totalAmount} = \text{subtotal} - \text{discountAmount} + \text{serviceCharge} + \text{deliveryFee} + \text{taxAmount}$$
- Ngăn chặn tuyệt đối các khoản chiết khấu/giảm giá lớn hơn `subtotal` hoặc các giá trị tiền âm.

### 6.2. Kiểm soát luồng trạng thái máy bộ trạng thái (FSM State Transitions)
Việc chuyển trạng thái của Đơn hàng (`OrderStatus`) và Món ăn (`OrderItemStatus`) không còn dùng chuỗi so sánh tự do mà được kiểm soát qua `OrderStateTransitionService` và `OrderItemStateTransitionService`.

- **Luồng `OrderStatus` (Đơn hàng):**
  `PENDING` -> `CONFIRMED` -> `PREPARING` -> `READY` -> `SERVED` -> `COMPLETED` (hoặc `CANCELLED` nếu chưa chế biến).
  *Quy tắc:* Không thể chuyển ngược từ `COMPLETED` về `PENDING`, không thể chuyển sang `READY` nếu không có món nào hoặc chưa chuẩn bị xong.
- **Luồng `OrderItemStatus` (Từng món ăn):**
  `PENDING` (Khách gọi) -> `PREPARING` (Bếp đang nấu) -> `READY` (Bếp báo xong) -> `SERVED` (Phục vụ đưa lên bàn).

### 6.3. Bảo mật theo dõi đơn công khai (`publicToken`)
Trang `/order/status/{id}` dành cho khách hàng quét QR hoặc chờ món không yêu cầu đăng nhập tài khoản hệ thống. Tuy nhiên để chống lộ thông tin đơn hàng do đoán ID tuần tự (1, 2, 3...), endpoint yêu cầu tham số **`token`**:
`/order/status/{id}?token={publicToken}`
Nếu `id` khớp nhưng sai hoặc thiếu `publicToken`, hệ thống từ chối truy cập hoặc ném ra `ResourceNotFoundException`.

---

## 7. GIAO DIỆN & TÌNH TRẠNG KỸ THUẬT CÁC MODULE (UI & CONTROLLERS)

- **Trang Khách (Customer):**
  - `/` (`index.html`): Giới thiệu quán, thông báo, dẫn vào thực đơn.
  - `/menu?tableId=X` (`menu.html`): Hiển thị menu chia theo category, giỏ hàng LocalStorage linh hoạt, tự động nhận dạng mã bàn từ tham số URL, gửi POST giỏ hàng về `/order/place`.
  - `/order/status/{id}?token=...` (`status.html`): Timeline tiến trình đơn hàng (tự động làm mới/cập nhật sau mỗi 5 giây), hiển thị mã VietQR thanh toán tự động tạo theo tổng tiền.
- **POS & Quản trị (Admin):**
  - `/admin/dashboard` (`dashboard.html`): Trang POS tổng quan, xem tình trạng tất cả bàn, mở hóa đơn, thanh toán nhanh, lọc chi nhánh.
  - `/admin/menu` & `/admin/tables`: Quản lý danh mục món ăn và bàn ăn theo chi nhánh hiện tại.
  - `/admin/inventory`: Quản lý nguyên liệu, hiển thị huy hiệu cảnh báo (Warning Badge) khi tồn kho chạm định mức tối thiểu.
  - `/admin/print/{id}` (`print.html`): Giao diện in phiếu hóa đơn nhiệt 80mm với trigger `window.print()`.
- **Các Dashboard nghiệp vụ riêng biệt:**
  - `/kitchen/dashboard` (`kitchen/dashboard.html`): Lọc ra toàn bộ các món `PENDING` hoặc `PREPARING` của chi nhánh, nút bấm chuyển `PREPARING` và `READY`.
  - `/waiter/dashboard` (`waiter/dashboard.html`): Lọc ra các món `READY` đang chờ bưng, nút bấm xác nhận đã phục vụ (`SERVED`).
  - `/cashier/dashboard` (`cashier/dashboard.html`): Quản lý thu chi, bàn `OCCUPIED` đang dùng và chuyển trạng thái `PAID`.

---

## 8. CÁC ĐIỂM CẦN LƯU Ý CHO DEVELOPER / AI KẾ TIẾP (NEXT STEPS & TECHNICAL DEBTS)

Khi tiếp tục viết code, refactor hoặc thêm tính năng mới cho `WEB_BUN_BO`, AI / Developer bắt buộc phải tuân thủ và lưu ý các điểm sau:
1. **Duy trì nguyên tắc cách ly chi nhánh (Branch Isolation - Milestone 2):** Bất kỳ API mới nào liên quan tới Entity (Món, Bàn, Đơn, Kho) đều phải đi qua `BranchAccessService` và sử dụng các repository method nhận `branchId`. Không bao giờ dùng `repository.findById(id)` trực tiếp từ Controller mà không kiểm tra quyền sở hữu chi nhánh.
2. **Sử dụng DTO & Validation cho mọi endpoint mới:** Không nhận raw JSON map hay `Double` cho các giá trị tiền tệ. Mọi giá tiền phải dùng `BigDecimal` và tính tại backend.
3. **Thư viện JSON Jackson:** Spring Boot 4.1.0 trong dự án này sử dụng namespace của Jackson 3.x nếu có thao tác thủ công (ưu tiên sử dụng `tools.jackson.databind.ObjectMapper` thay vì `com.fasterxml.jackson` nếu viết utility).
4. **Optimistic Locking (`version` field):** Khi cập nhật Đơn hàng hay Bàn ăn, phải bắt xử lý hoặc để JPA kiểm soát `ObjectOptimisticLockingFailureException` khi nhiều vai trò cùng thao tác đồng thời.
5. **Đơn vị Kho (`InventoryItem.quantity`, `minThreshold`):** Hiện vẫn sử dụng `Double` vì đây là định lượng khối lượng/thể tích thực phẩm (kg, lít, bát), không phải tiền tệ. Tuy nhiên ở milestone tiếp theo về Inventory, cần rà soát lại độ chính xác hoặc đổi sang `BigDecimal` nếu cần tính định mức chi phí nguyên liệu chi tiết.
