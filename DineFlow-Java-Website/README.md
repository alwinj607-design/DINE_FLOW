# DineFlow — Java Restaurant Menu & Order System

A runnable Spring Boot + HTML/CSS/JavaScript project inspired by the supplied screenshots.

## Requirements
- JDK 21
- Maven 3.9+ (or use your IDE's Maven support)
- Internet access on the first build so Maven can download dependencies

## Run on Windows
1. Extract the ZIP.
2. Open the extracted `DineFlow-Java-Website` folder in VS Code or IntelliJ.
3. In a terminal in that folder, run:
   ```powershell
   mvn spring-boot:run
   ```
4. Open http://localhost:8080
5. API documentation: http://localhost:8080/swagger-ui.html

If `mvn` is not recognized, install Maven or run the project through IntelliJ's Maven panel. Ensure `java -version` reports Java 21.

## Demo access
- Staff PIN: `1234`
- Admin password: `admin123`

This is a student/demo project. Authentication is deliberately lightweight and must be replaced with proper Spring Security, hashed credentials, CSRF protection and role-based authorization before public deployment. The H2 database persists locally under `./data`.

## Features
- Customer menu search and category filter
- Add/remove quantities in cart, dine-in/takeaway/delivery selection
- Coupon `SAVE10`, totals and order receipt/print
- Customer latest-order status tracker
- Staff login and order workflow: Placed → Received → In Progress → Completed
- Admin login, add/edit/publish/remove menu dishes, dashboard totals
- REST API and Swagger/OpenAPI docs
