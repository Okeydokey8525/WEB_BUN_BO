# WEB_BUN_BO — Hệ thống quản lý nhà hàng Bún Bò

WEB_BUN_BO là ứng dụng quản lý nhà hàng/POS dạng **modular monolith**, xây dựng bằng Spring Boot. Hệ thống phục vụ đặt món, vận hành theo vai trò, thanh toán/hoàn tiền, kho–công thức, ca làm việc, báo cáo CSV và nhật ký hoạt động có phân tách theo chi nhánh.

> Mã ứng dụng nằm trong thư mục [`demo`](demo). README này phản ánh trạng thái code tại nhánh hiện hành, không phải kế hoạch tính năng tương lai.

## Mục lục

- [Chức năng](#chức-năng)
- [Công nghệ](#công-nghệ)
- [Kiến trúc và cấu trúc](#kiến-trúc-và-cấu-trúc)
- [Yêu cầu môi trường](#yêu-cầu-môi-trường)
- [Cấu hình database và profile](#cấu-hình-database-và-profile)
- [Cài đặt, chạy và đóng gói](#cài-đặt-chạy-và-đóng-gói)
- [Tài khoản demo](#tài-khoản-demo)
- [Phân quyền và route](#phân-quyền-và-route)
- [API báo cáo, audit và CSV](#api-báo-cáo-audit-và-csv)
- [Flyway migrations](#flyway-migrations)
- [Kiểm thử](#kiểm-thử)
- [Trạng thái UI và smoke test](#trạng-thái-ui-và-smoke-test)
- [Tài liệu, giới hạn và roadmap](#tài-liệu-giới-hạn-và-roadmap)
- [Bảo mật và quy ước Git](#bảo-mật-và-quy-ước-git)

## Chức năng

### Khách hàng

- Xem thực đơn, tìm kiếm/lọc món và đặt món.
- Theo dõi đơn qua route công khai dùng mã token của đơn.
- Đăng ký, đăng nhập, cập nhật hồ sơ, đổi mật khẩu và quản lý món yêu thích.

### Vận hành nhà hàng

- **ADMIN**: dashboard, món ăn, bàn ăn, hóa đơn in, kho, công thức, báo cáo, CSV export và audit log.
- **CASHIER**: POS, xem đơn, thanh toán, hoàn tiền, lịch sử giao dịch, mở/đóng và tra cứu ca làm việc.
- **WAITER**: xem dashboard phục vụ và xác nhận phục vụ món/bàn theo workflow.
- **KITCHEN**: xem dashboard bếp và cập nhật món đã sẵn sàng.
- **INVENTORY**: truy cập phần kho và công thức trong chi nhánh của mình.

### Thanh toán, kho, ca và audit

- Payment ledger cho thanh toán và hoàn tiền; tiền dùng `BigDecimal` trong domain payment.
- Inventory ledger cho nhập/điều chỉnh/trừ kho/hoàn kho theo đơn; tồn kho và định lượng công thức dùng `BigDecimal`.
- Recipe gắn món ăn với nguyên liệu thuộc cùng chi nhánh.
- Mở/đóng ca, đối soát tiền mặt và tổng hợp giao dịch theo ca.
- Structured audit ghi các workflow shift, payment/refund, inventory và recipe; audit log được scope theo chi nhánh.
- Sáu loại CSV: tổng doanh thu, doanh thu ngày, phương thức thanh toán, món bán chạy, tiêu thụ kho và ca làm việc.

## Công nghệ

| Thành phần | Cấu hình đã xác minh |
| --- | --- |
| Ngôn ngữ | Java 17 (`demo/pom.xml`) |
| Framework | Spring Boot 4.1.0 |
| Web | Spring MVC, Thymeleaf, Jakarta Validation |
| Security | Spring Security, form login, BCrypt, CSRF cho form (một số API được cấu hình loại trừ CSRF) |
| Persistence | Spring Data JPA / Hibernate |
| Database dev/test | H2 in-memory ở chế độ PostgreSQL compatibility |
| Database production | PostgreSQL qua driver runtime |
| Migration | Flyway Core và Flyway PostgreSQL |
| Frontend | HTML, CSS, vanilla JavaScript, Thymeleaf Security Extras |
| Build/test | Maven Wrapper, JUnit 5, Spring Boot Test, Spring Security Test |
| Tiện ích dev | Lombok, Spring Boot DevTools |

## Kiến trúc và cấu trúc

Ứng dụng tổ chức theo lớp: controller nhận HTTP/UI, service chứa nghiệp vụ và transaction, repository truy vấn JPA, model là entity/enums, DTO bảo vệ binding/response, còn `config`, `security` và `exception` xử lý cấu hình, xác thực/phân quyền và lỗi tập trung.

```text
WEB_BUN_BO/
├── demo/
│   ├── .mvn/                         # Maven Wrapper runtime
│   ├── docs/
│   │   └── milestone-6-closure.md
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/demo/
│   │   │   │   ├── config/ controller/ dto/ exception/
│   │   │   │   ├── model/ repository/ security/ service/
│   │   │   └── resources/
│   │   │       ├── db/migration/
│   │   │       ├── static/
│   │   │       └── templates/
│   │   └── test/java/com/example/demo/
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml
├── .gitignore
└── README.md
```

## Yêu cầu môi trường

- Java 17.
- Git (để clone repository).
- Không cần cài Maven riêng: dự án có Maven Wrapper.
- PostgreSQL chỉ cần khi chạy profile `prod`; dev và test dùng H2 in-memory.
- IDE tùy chọn: IntelliJ IDEA, Eclipse hoặc VS Code có Java extension.
- Cổng mặc định của Spring Boot: `8080`.

Kiểm tra Java:

```powershell
java -version
```

## Cấu hình database và profile

| Profile | Database | Điểm chính |
| --- | --- | --- |
| `dev` (mặc định) | `jdbc:h2:mem:bunbodb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL` | Flyway bật, H2 Console tại `/h2-console`, SQL log bật |
| `test` | `jdbc:h2:mem:bunbo_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL` | Flyway bật, H2 Console tắt, `app.db.seed-java=false` |
| `prod` | PostgreSQL từ `DB_URL` | `ddl-auto=validate`, Flyway bật, static upload từ `APP_UPLOAD_DIR` |

Production cần cung cấp biến môi trường, không đưa mật khẩu thật vào source:

```properties
DB_URL=jdbc:postgresql://localhost:5432/web_bun_bo
DB_USERNAME=your_username
DB_PASSWORD=your_password
APP_UPLOAD_DIR=/absolute/path/to/uploads
SESSION_TIMEOUT=30m
```

Flyway chạy tự động từ `classpath:db/migration`. Khi dùng PostgreSQL, hãy tạo database đích trước, cấu hình các biến trên và để Flyway quản lý schema; không dùng H2/test database cho production.

## Cài đặt, chạy và đóng gói

### Chạy local trên Windows PowerShell

```powershell
git clone <repository-url>
cd WEB_BUN_BO\demo
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run
```

Mở ứng dụng tại <http://localhost:8080>. Các route hữu ích:

- Trang khách hàng: <http://localhost:8080/>
- Đăng nhập: <http://localhost:8080/login>
- Dashboard admin: <http://localhost:8080/admin/dashboard>
- H2 Console (chỉ profile `dev`): <http://localhost:8080/h2-console>

### Chạy production profile

```powershell
cd WEB_BUN_BO\demo
$env:SPRING_PROFILES_ACTIVE = "prod"
.\mvnw.cmd spring-boot:run
```

### Đóng gói JAR

`artifactId` là `demo`, phiên bản là `0.0.1-SNAPSHOT`, không có `finalName` override. JAR Spring Boot sinh ra là `target\demo-0.0.1-SNAPSHOT.jar`.

```powershell
cd WEB_BUN_BO\demo
.\mvnw.cmd clean package
java -jar target\demo-0.0.1-SNAPSHOT.jar
```

## Tài khoản demo

[`V2__seed_data.sql`](demo/src/main/resources/db/migration/V2__seed_data.sql) seed năm tài khoản với BCrypt hash được chú thích là mật khẩu `admin123`. `DataInitializer` cũng dùng cùng mật khẩu khi seed Java.

| Vai trò | Username | Password | Trang sau đăng nhập |
| --- | --- | --- | --- |
| `ROLE_ADMIN` | `admin` | `admin123` | `/admin/dashboard` |
| `ROLE_CASHIER` | `cashier` | `admin123` | `/cashier/dashboard` |
| `ROLE_WAITER` | `waiter` | `admin123` | `/waiter/dashboard` |
| `ROLE_KITCHEN` | `kitchen` | `admin123` | `/kitchen/dashboard` |
| `ROLE_INVENTORY` | `inventory` | `admin123` | `/admin/inventory` |

Các credential này chỉ dành cho local/demo. Hãy đổi hoặc thay thế bằng seed/secret riêng trước khi triển khai production.

## Phân quyền và route

Security dùng form login tại `/login`; anonymous truy cập tài nguyên bảo vệ sẽ được chuyển đến trang đăng nhập. `ROLE_ADMIN` và các role vận hành đều bị scope bởi branch trong service/repository; tham số `branchId` từ client không được phép vượt branch hiện hành.

| Khu vực | Route chính | Quyền theo `SecurityConfig` |
| --- | --- | --- |
| Báo cáo/audit API | `/api/admin/audit-logs/**`, `/api/admin/reports/**` | `ROLE_ADMIN` |
| Admin UI | `/admin/**` | `ROLE_ADMIN` |
| Kho/công thức | `/admin/inventory/**`, `/admin/recipes/**` | `ROLE_ADMIN` hoặc `ROLE_INVENTORY` |
| Thu ngân/ca | `/cashier/**`, `/cashier/shifts/**` | `ROLE_ADMIN` hoặc `ROLE_CASHIER` |
| Phục vụ | `/waiter/**` | `ROLE_ADMIN` hoặc `ROLE_WAITER` |
| Bếp | `/kitchen/**` | `ROLE_ADMIN` hoặc `ROLE_KITCHEN` |
| Public | `/`, `/menu`, `/search`, `/about`, `/order/status/**` | Không cần đăng nhập |

CSRF được giữ cho form; config hiện loại trừ `/api/**`, H2 Console và một số endpoint public/profile. Không coi API bị loại trừ CSRF là public: authorization vẫn áp dụng theo matcher.

## API báo cáo, audit và CSV

### Audit API

| Method | Route | Quyền | Filter/chức năng |
| --- | --- | --- | --- |
| GET | `/api/admin/audit-logs` | ADMIN | `branchId`, `action`, `username`, `from`, `to`, `page`, `size`; trả `Page<AuditLogSummary>` |
| GET | `/api/admin/audit-logs/summary` | ADMIN | Cùng filter cơ bản; trả tổng record và branch scope |

`branchId` chỉ hợp lệ khi trùng branch của người đang đăng nhập. Kích thước trang audit bị giới hạn tối đa 100.

### CSV report API

Tất cả endpoint dưới đây yêu cầu `ROLE_ADMIN`, cần `from` và `to` ở định dạng ISO date (`yyyy-MM-dd`), trả `text/csv;charset=UTF-8` cùng tên file có khoảng ngày.

| Route | Query bổ sung | Nội dung |
| --- | --- | --- |
| `GET /api/admin/reports/revenue/export.csv` | `branchId` tùy chọn | Tổng doanh thu, hoàn tiền, doanh thu thuần, số đơn thanh toán |
| `GET /api/admin/reports/daily-revenue/export.csv` | `branchId` tùy chọn | Doanh thu theo ngày |
| `GET /api/admin/reports/payment-methods/export.csv` | `branchId` tùy chọn | Phân rã theo phương thức thanh toán |
| `GET /api/admin/reports/top-dishes/export.csv` | `limit` (mặc định 10), `branchId` | Món bán chạy |
| `GET /api/admin/reports/inventory-consumption/export.csv` | `limit` (mặc định 10), `branchId` | Tiêu thụ/hoàn nguyên liệu |
| `GET /api/admin/reports/shifts/export.csv` | `branchId` tùy chọn | Tổng hợp ca làm việc |

Trong code hiện tại, `ReportingCsvController` chỉ map sáu endpoint export CSV nêu trên. Không có reporting JSON endpoint riêng được map ở controller; đây là điểm cần lưu ý khi bảo trì trang `/admin/reports`.

### Shift routes

`/cashier/shifts/current`, `/history`, `/{id}`, `/open` và `/{id}/close` lần lượt hỗ trợ xem ca hiện tại/lịch sử/chi tiết và POST mở–đóng ca. Các thao tác POST dùng validation và quyền ADMIN/CASHIER.

## Flyway migrations

| Version | Migration | Mục đích |
| --- | --- | --- |
| V1 | `V1__init_schema.sql` | Core schema: branch, role, user, menu, table, order, inventory, recipe và activity log |
| V2 | `V2__seed_data.sql` | Seed branch, role, user demo, món, bàn, kho và recipe |
| V3 | `V3__user_profile_and_favorites.sql` | Hồ sơ người dùng và món yêu thích |
| V4 | `V4__expand_url_columns.sql` | Mở rộng các URL column |
| V5 | `V5__order_public_token.sql` | Public token cho theo dõi đơn |
| V6 | `V6__stabilize_order_domain.sql` | Ổn định order domain và money/status |
| V7 | `V7__payment_transactions.sql` | Payment transaction ledger |
| V8 | `V8__inventory_transactions_and_stock_precision.sql` | Inventory transaction ledger và precision tồn kho/công thức |
| V9 | `V9__work_shifts_and_structured_audit.sql` | Work shift và structured audit |

Không sửa migration đã phát hành. Thay đổi schema mới phải được thêm qua migration Flyway tiếp theo.

## Kiểm thử

Test dùng JUnit 5, Spring Boot Test, H2 profile `test` và Spring Security Test.

```powershell
cd WEB_BUN_BO\demo
.\mvnw.cmd test-compile
.\mvnw.cmd clean test
```

Kết quả full regression gần nhất tại commit `635de64`: **203 tests, 0 failures, 0 errors, BUILD SUCCESS**. Nhóm test tiêu biểu gồm:

- service unit tests: payment, inventory, recipe, shift, reporting và audit;
- repository integration tests cho các aggregate báo cáo;
- security/branch-isolation integration tests;
- `ReportingCsvControllerTests` và `CsvExportServiceTests`;
- `AuditWorkflowIntegrationTests` và `AuditLogControllerTests`;
- `AdminReportingPageTests` và `AdminAuditLogPageTests` cho MVC/template.

Xem toàn bộ tại [`demo/src/test/java/com/example/demo`](demo/src/test/java/com/example/demo).

## Trạng thái UI và smoke test

Desktop smoke trên local H2 seed data đã xác minh:

- anonymous vào `/admin/reports` và `/admin/audit-logs` được redirect `/login`;
- ADMIN mở được cả hai trang; sidebar có menu Báo cáo và Nhật ký hoạt động;
- `admin-reports.js` và `admin-audit-logs.js` tải thành công, không có console error trong smoke;
- CASHIER bị chặn hai trang ADMIN và nội dung page không được render.

Chưa xác minh thủ công đầy đủ: responsive mobile/tablet (`375×667`, `390×844`, `768×1024`) và mở đủ sáu file CSV bằng Excel. Không tuyên bố hai phần này đã hoàn tất.

### Checklist smoke ngắn

1. Đăng nhập `admin`, mở Reports/Audit Logs, kiểm tra console/network.
2. Kiểm tra `cashier` và anonymous không truy cập được vùng ADMIN.
3. Thử filter ngày/action/username và tải sáu CSV với filter ngày.
4. Kiểm tra sidebar, filter, table horizontal scroll và button overflow tại ba breakpoint trên.

## Tài liệu, giới hạn và roadmap

Tài liệu milestone hiện có: [`demo/docs/milestone-6-closure.md`](demo/docs/milestone-6-closure.md).

### Giới hạn hiện tại

- Chưa có browser automation/E2E; QA mobile/tablet và mở CSV bằng Excel còn cần thực hiện thủ công.
- Chưa có chart nâng cao, realtime dashboard, audit CSV export hoặc export PDF/XLSX.
- UI reporting/audit là bản cơ bản; reporting JSON endpoint chưa được controller map như đã nêu ở phần API.
- Credential demo và cấu hình database production phải được externalize/siết chặt trước deployment thực tế.

### Roadmap ngắn

- Hoàn tất responsive và CSV Excel QA; thêm browser automation.
- Bổ sung reporting JSON endpoint hoặc điều chỉnh UI để dùng contract API thống nhất.
- Thêm chart, pagination/filter nâng cao và audit detail modal.
- Hardening deployment, secret management, performance/load testing.

## Bảo mật và quy ước Git

- Không commit password, database credential hay `.env` chứa secret.
- Đổi tài khoản demo trước production; dùng environment variables cho PostgreSQL và upload directory.
- Giữ CSRF, role matcher và branch scoping khi thêm endpoint mới.
- Dùng feature branch, chạy test phù hợp trước commit và review các thay đổi migration/security kỹ lưỡng.
- Commit message hiện theo tiền tố như `feat:`, `fix:`, `test:` và `docs:`. Khi có file nội bộ trong working tree, stage từng file cụ thể thay vì dùng `git add .`.
