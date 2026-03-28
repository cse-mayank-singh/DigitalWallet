# 💰 Digital Wallet & Rewards System

## 📌 Project Overview

This is a **microservices-based Digital Wallet & Loyalty System** built using Spring Boot.
It allows users to manage wallet balance, perform transactions, and earn/redeem rewards.

---

## 🏗️ Architecture

* Microservices Architecture
* Service Discovery using Eureka
* API Gateway for routing
* REST APIs (Spring Boot)

---

## 📂 Project Structure

```
digital-wallet-system/
│
├── eureka-server/
├── api-gateway/
├── auth-service/
├── user-service/
├── wallet-service/
├── rewards-service/
├── admin-service/
```

---

## 🚀 Tech Stack

* Java 21
* Spring Boot
* Spring Cloud (Eureka, Gateway)
* PostgreSQL
* Maven

---

## 🔄 Git Workflow (VERY IMPORTANT)

### 🌳 Branches

```
main   → production (protected)
dev    → integration branch
feature/* → individual work
```

---

## 👨‍💻 How Team Should Work

### 1️⃣ Clone Repository

```
git clone <repo-url>
cd digital-wallet-system
git checkout dev
```

---

### 2️⃣ Create Feature Branch

```
git checkout -b feature/<your-service-name>
```

Example:

```
feature/auth-service
feature/wallet-service
```

---

### 3️⃣ Do Your Work

* Work ONLY in your assigned service folder
* Do not modify other services

---

### 4️⃣ Commit & Push

```
git add .
git commit -m "your message"
git push origin feature/<branch-name>
```

---

### 5️⃣ Create Pull Request

On GitHub:

```
feature/* → dev
```

---

### 6️⃣ Merge Rules

* Do NOT push directly to `main`
* All code must go through PR
* `dev → main` only after testing

---

## ⚙️ How to Run Services

### 1️⃣ Start Eureka Server

```
cd eureka-server
./mvnw spring-boot:run
```

👉 http://localhost:8761

---

### 2️⃣ Start API Gateway

```
cd api-gateway
./mvnw spring-boot:run
```

👉 http://localhost:8080

---

### 3️⃣ Start Other Services

Each service:

```
cd <service-name>
./mvnw spring-boot:run
```

---

## 🔗 API Access (via Gateway)

Example:

```
http://localhost:8080/api/auth/login
```

---

## ⚠️ Important Rules

* ❌ Do NOT push directly to `main`
* ❌ Do NOT work on `dev` directly
* ✅ Always use feature branches
* ✅ Always create Pull Request

---

## 👥 Team Responsibilities

| Service          | Owner     |
| ---------------- | --------- |
| Eureka + Gateway | Team Lead |
| Auth Service     | Member 1  |
| User Service     | Member 2  |
| Wallet Service   | Member 3  |
| Rewards/Admin    | Member 4  |

---

## 🎯 Goal

* Secure Wallet System
* Loyalty & Rewards Engine
* Scalable Microservices Architecture

---

## 📌 Status

🚧 In Development

---
