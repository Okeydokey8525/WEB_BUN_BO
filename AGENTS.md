# AGENTS.md

## 🧠 Project Overview
This project is a full-stack web application for a Vietnamese "Bún Bò" restaurant.

The system includes:
- Customer-facing website (menu, ordering)
- Admin dashboard (manage dishes, orders)
- Backend API with Flask
- SQLite database

---

## ⚙️ Tech Stack
- Backend: Python (Flask)
- Frontend: HTML, CSS, Bootstrap 5, JavaScript
- Database: SQLite
- Template Engine: Jinja2

---

## 🎯 Features

### 1. Customer Side
- View homepage with restaurant info
- View menu (name, price, image)
- Add items to cart
- Place orders

### 2. Admin Panel
- Login system (simple session-based)
- Add / Edit / Delete dishes
- View orders

---

## 📂 Project Structure
- app.py → main Flask app
- templates/ → HTML files
- static/ → CSS, JS, images
- database.db → SQLite database

---

## 🤖 Agent Responsibilities

### Agent: BackendAgent
- Build Flask app
- Create routes:
  - /
  - /menu
  - /cart
  - /order
  - /admin
- Handle form submissions
- Connect SQLite

### Agent: FrontendAgent
- Build responsive UI using Bootstrap 5
- Create pages:
  - index.html
  - menu.html
  - cart.html
  - admin.html

### Agent: DatabaseAgent
- Design SQLite schema:
  - dishes (id, name, price, image)
  - orders (id, items, total, created_at)

---

## 🚀 Expected Output
- Fully runnable Flask project
- Clean UI
- Working CRUD for admin
- Order system functional

---

## 🧪 Run Instructions
- Install dependencies
- Run Flask app
- Access via browser

---

## 🎨 UI Style
- Modern, clean restaurant style
- Use Bootstrap components
- Include navbar, cards, buttons