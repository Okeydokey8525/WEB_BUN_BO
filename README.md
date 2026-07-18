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
