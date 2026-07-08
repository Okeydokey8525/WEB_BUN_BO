-- V2__seed_data.sql
-- Flyway migration script seeding default branch, roles, users, menu dishes, tables, inventory, and recipes

-- 1. Seed Branch
INSERT INTO branches (name, address, phone, status) 
VALUES ('Chi nhánh 1 (Quận 1)', '123 Đường Số 4, Q.1, TP.HCM', '0987.654.321', 'ACTIVE');

-- 2. Seed Roles
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');
INSERT INTO roles (name) VALUES ('ROLE_CASHIER');
INSERT INTO roles (name) VALUES ('ROLE_WAITER');
INSERT INTO roles (name) VALUES ('ROLE_KITCHEN');
INSERT INTO roles (name) VALUES ('ROLE_INVENTORY');
INSERT INTO roles (name) VALUES ('ROLE_CUSTOMER');

-- 3. Seed Users (with BCrypt encoded password for 'admin123': $2a$10$i9Iz8YFPa2nkqmfJr3uakeBrO.a2/97nPMSea/VbmaGa0A82AOMxK)
INSERT INTO users (username, password, full_name, role_id, branch_id, enabled)
VALUES ('admin', '$2a$10$i9Iz8YFPa2nkqmfJr3uakeBrO.a2/97nPMSea/VbmaGa0A82AOMxK', 'Chủ cửa hàng (Admin)', 1, 1, TRUE);

INSERT INTO users (username, password, full_name, role_id, branch_id, enabled)
VALUES ('cashier', '$2a$10$i9Iz8YFPa2nkqmfJr3uakeBrO.a2/97nPMSea/VbmaGa0A82AOMxK', 'Nhân viên Thu ngân', 2, 1, TRUE);

INSERT INTO users (username, password, full_name, role_id, branch_id, enabled)
VALUES ('waiter', '$2a$10$i9Iz8YFPa2nkqmfJr3uakeBrO.a2/97nPMSea/VbmaGa0A82AOMxK', 'Nhân viên Phục vụ', 3, 1, TRUE);

INSERT INTO users (username, password, full_name, role_id, branch_id, enabled)
VALUES ('kitchen', '$2a$10$i9Iz8YFPa2nkqmfJr3uakeBrO.a2/97nPMSea/VbmaGa0A82AOMxK', 'Nhân viên Bếp', 4, 1, TRUE);

INSERT INTO users (username, password, full_name, role_id, branch_id, enabled)
VALUES ('inventory', '$2a$10$i9Iz8YFPa2nkqmfJr3uakeBrO.a2/97nPMSea/VbmaGa0A82AOMxK', 'Nhân viên Kho', 5, 1, TRUE);

-- 4. Seed Dishes
INSERT INTO dishes (name, price, image_url, category, is_available, branch_id)
VALUES ('Bún Bò Đặc Biệt', 65000.0, 'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=600&auto=format&fit=crop', 'Bún Bò', TRUE, 1);

INSERT INTO dishes (name, price, image_url, category, is_available, branch_id)
VALUES ('Bún Bò Tái Nạm Chả', 55000.0, 'https://images.unsplash.com/photo-1594998893017-361470be31d0?w=600&auto=format&fit=crop', 'Bún Bò', TRUE, 1);

INSERT INTO dishes (name, price, image_url, category, is_available, branch_id)
VALUES ('Bún Bò Giò Heo', 50000.0, 'https://images.unsplash.com/photo-1588166524941-3bf61a9c41db?w=600&auto=format&fit=crop', 'Bún Bò', TRUE, 1);

INSERT INTO dishes (name, price, image_url, category, is_available, branch_id)
VALUES ('Chả Cua Thêm (1 viên)', 12000.0, 'https://images.unsplash.com/photo-1541544741938-0af808871cc0?w=600&auto=format&fit=crop', 'Món thêm', TRUE, 1);

INSERT INTO dishes (name, price, image_url, category, is_available, branch_id)
VALUES ('Thịt Nạm Bò Thêm', 18000.0, 'https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=600&auto=format&fit=crop', 'Món thêm', TRUE, 1);

INSERT INTO dishes (name, price, image_url, category, is_available, branch_id)
VALUES ('Trà Đá', 5000.0, 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=600&auto=format&fit=crop', 'Nước uống', TRUE, 1);

INSERT INTO dishes (name, price, image_url, category, is_available, branch_id)
VALUES ('Nước Ngọt (Coca/Pepsi)', 15000.0, 'https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=600&auto=format&fit=crop', 'Nước uống', TRUE, 1);

-- 5. Seed Restaurant Tables
INSERT INTO restaurant_tables (table_number, status, branch_id) VALUES ('Bàn 1', 'FREE', 1);
INSERT INTO restaurant_tables (table_number, status, branch_id) VALUES ('Bàn 2', 'FREE', 1);
INSERT INTO restaurant_tables (table_number, status, branch_id) VALUES ('Bàn 3', 'FREE', 1);
INSERT INTO restaurant_tables (table_number, status, branch_id) VALUES ('Bàn 4', 'FREE', 1);
INSERT INTO restaurant_tables (table_number, status, branch_id) VALUES ('Bàn 5', 'FREE', 1);

-- 6. Seed Inventory Items
INSERT INTO inventory (ingredient_name, quantity, unit, min_threshold, branch_id)
VALUES ('Bún sợi to', 35.0, 'kg', 15.0, 1);

INSERT INTO inventory (ingredient_name, quantity, unit, min_threshold, branch_id)
VALUES ('Rau sống ăn kèm', 12.0, 'kg', 5.0, 1);

INSERT INTO inventory (ingredient_name, quantity, unit, min_threshold, branch_id)
VALUES ('Thịt nạm bò', 3.2, 'kg', 8.0, 1);

INSERT INTO inventory (ingredient_name, quantity, unit, min_threshold, branch_id)
VALUES ('Chả cua', 45.0, 'viên', 100.0, 1);

INSERT INTO inventory (ingredient_name, quantity, unit, min_threshold, branch_id)
VALUES ('Nước cốt xương hầm', 8.5, 'lít', 20.0, 1);

-- 7. Seed Recipes
INSERT INTO recipes (dish_id, branch_id) VALUES (1, 1); -- Recipe for Bún Bò Đặc Biệt
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (1, 'Thịt nạm bò', 0.15, 'kg');
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (1, 'Chả cua', 1.0, 'viên');
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (1, 'Bún sợi to', 0.15, 'kg');
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (1, 'Nước cốt xương hầm', 0.2, 'lít');

INSERT INTO recipes (dish_id, branch_id) VALUES (2, 1); -- Recipe for Bún Bò Tái Nạm Chả
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (2, 'Thịt nạm bò', 0.10, 'kg');
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (2, 'Chả cua', 1.0, 'viên');
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (2, 'Bún sợi to', 0.15, 'kg');
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (2, 'Nước cốt xương hầm', 0.2, 'lít');

INSERT INTO recipes (dish_id, branch_id) VALUES (3, 1); -- Recipe for Bún Bò Giò Heo
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (3, 'Bún sợi to', 0.15, 'kg');
INSERT INTO recipe_items (recipe_id, ingredient_name, amount, unit) VALUES (3, 'Nước cốt xương hầm', 0.2, 'lít');
