# WEB_BUN_BO
web bán bún bò

# 📦 Project Context & Migration Status

*   **Original Scope**: The repository `WEB_BUN_BO` was cloned with an initial Python/Flask + SQLite structure.
*   **Current Scope**: The project has been fully migrated into a **Java Spring Boot 4.1.0** web application using **Java 25**.
*   **Folder Location**: All active code is located inside the [`demo`](file:///c:/2001230490_LeDucLuong/HK%20VI/Web%20bun%20bo/demo) directory.
*   **Database Mode**: Currently configured to use an **in-memory H2 Database** simulation (as requested by the user to run easily without initial setup). It automatically seeds mock tables, dishes, and stock on startup.

---

## 🛠️ Tech Stack & Key Configurations

*   **Language**: Java 25 (LTS).
*   **Backend**: Spring Boot 4.1.0, Spring Data JPA, Lombok.
*   **Frontend**: Thymeleaf templates, custom vanilla CSS (Warm terracotta `#C0392B` theme, no Tailwind), FontAwesome icons.
*   **JSON Serialization**: Spring Boot 4.x uses **Jackson 3.x**. If you need to serialize/deserialize JSON, use the **`tools.jackson`** namespace instead of the older `com.fasterxml.jackson` namespace.
    *   *Example*: `import tools.jackson.databind.ObjectMapper;` and `import tools.jackson.core.type.TypeReference;`

---

## 📂 Project Directory Structure

```
c:\2001230490_LeDucLuong\HK VI\Web bun bo\demo
├── pom.xml                                      <- Configured with H2, JPA, Thymeleaf, and Lombok
└── src/main
    ├── java/com/example/demo
    │   ├── DemoApplication.java                 <- Main entrypoint
    │   ├── config
    │   │   └── DataInitializer.java             <- Seeds mock dishes, tables, and low stock items
    │   ├── model
    │   │   ├── Dish.java                        <- Menu dish definition
    │   │   ├── RestaurantTable.java             <- Dining table occupancy state (FREE, OCCUPIED, ORDERING)
    │   │   ├── Order.java                       <- Guest bill details & payment status
    │   │   ├── OrderItem.java                   <- Ordered item quantity and locked price
    │   │   └── InventoryItem.java               <- Warehouse ingredients & alarm warning levels
    │   ├── repository                           <- JPA Repository Interfaces
    │   │   ├── DishRepository.java
    │   │   ├── RestaurantTableRepository.java
    │   │   ├── OrderRepository.java
    │   │   └── InventoryRepository.java         <- Includes custom JPQL query for low-stock warnings
    │   └── controller
    │       ├── CustomerController.java          <- Home, menu, LocalStorage cart, table qr-prefill, VietQR image URL builder
    │       └── AdminController.java             <- Staff POS dash, stats calculator, stock updates, receipt printing
    └── resources
        ├── application.properties               <- Configured with in-memory H2 and enabled /h2-console
        ├── static/css/style.css                 <- Color variables, micro-animations, table nodes, and print overrides
        └── templates
            ├── fragments/layout.html            <- Common navbar structure
            ├── customer
            │   ├── index.html                   <- Homepage with restaurant introduction
            │   ├── menu.html                    <- Guest menu catalog with JS cart logic
            │   └── status.html                  <- Active order timeline (5s refresh) & VietQR code display
            └── admin
                ├── dashboard.html               <- POS, tables status, order actions
                ├── inventory.html               <- Raw ingredient levels & warning badges
                ├── menu.html                    <- Menu CRUD interface (add, edit pre-fills, delete, availability toggles)
                └── print.html                   <- 80mm thermal receipt ticket designed with window.print() trigger
```

---

## 🏃 How to Build and Run

Inside the [`demo`](file:///c:/2001230490_LeDucLuong/HK%20VI/Web%20bun%20bo/demo) directory, run:
*   **Compile**: `.\mvnw.cmd clean compile`
*   **Start server**: `.\mvnw.cmd spring-boot:run`

### Local endpoints:
*   Customer Hub: `http://localhost:8080/`
*   QR code simulation link (e.g. Table 3): `http://localhost:8080/menu?tableId=3`
*   POS Admin Control Panel: `http://localhost:8080/admin/dashboard`
*   Database visual explorer: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:bunbodb`, User: `sa`, Pass: empty)

---

## 🔮 Next Steps & Recommendations for Future Agents

1.  **Switching to Microsoft SQL Server**: 
    When the user is ready to migrate from the simulation database, add the `mssql-jdbc` driver dependency to the `pom.xml`, and update `application.properties` with their credentials.
2.  **Dish Image Uploads**:
    Enhance the Admin Menu CRUD page to support image uploads to a static local folder or cloud bucket, rather than relying on URL input text fields.
3.  **Authentication & Security**:
    Introduce Spring Security for the `/admin/**` routes to ensure cashiers/managers must log in before accessing the POS and inventory.
    