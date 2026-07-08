-- V3__user_profile_and_favorites.sql
-- Thêm các trường thông tin cá nhân và bảng lưu món ăn yêu thích

ALTER TABLE users ADD COLUMN avatar_url VARCHAR(1000);
ALTER TABLE users ADD COLUMN phone VARCHAR(50);
ALTER TABLE users ADD COLUMN address VARCHAR(500);
ALTER TABLE users ADD COLUMN email VARCHAR(255);

CREATE TABLE user_favorites (
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    dish_id BIGINT REFERENCES dishes(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, dish_id)
);

-- Cập nhật thông tin cho user admin
UPDATE users SET 
    full_name = 'Lê Đức Lương',
    avatar_url = 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400&auto=format&fit=crop',
    phone = '0987.654.321',
    address = '123 Đường Số 4, Q.1, TP.HCM',
    email = 'luang@bunbogiatruyen.vn'
WHERE username = 'admin';
