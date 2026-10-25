package com.dineflow;

import com.dineflow.security.AuthFilter;
import com.dineflow.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final MenuRepository menu;
    private final OrderRepository orders;
    private final JwtService jwtService;
    private final String adminPassword;
    private final String staffPin;

    public ApiController(
            MenuRepository m,
            OrderRepository o,
            JwtService jwtService,
            @Value("${dineflow.auth.admin-password:admin123}") String adminPassword,
            @Value("${dineflow.auth.staff-pin:1234}") String staffPin) {
        this.menu = m;
        this.orders = o;
        this.jwtService = jwtService;
        this.adminPassword = adminPassword;
        this.staffPin = staffPin;
    }

    @GetMapping("/menu")
    public List<MenuItem> menu() {
        return menu.findAll().stream().sorted(Comparator.comparing(x -> x.id)).collect(Collectors.toList());
    }

    @GetMapping("/menu/published")
    public List<MenuItem> published() {
        return menu.findByPublishedTrueOrderByIdAsc();
    }

    @PostMapping("/admin/login")
    public Map<String, Object> adminLogin(@RequestBody(required = false) Map<String, String> body, HttpServletResponse response) {
        String password = body != null ? body.get("password") : null;
        if (password == null || !adminPassword.equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect admin password");
        }
        String token = jwtService.generateToken("admin", "ADMIN");
        setAuthCookie(response, token);
        return Map.of(
                "ok", true,
                "role", "ADMIN",
                "token", token,
                "user", Map.of("username", "admin", "role", "ADMIN")
        );
    }

    @PostMapping("/staff/login")
    public Map<String, Object> staffLogin(@RequestBody(required = false) Map<String, String> body, HttpServletResponse response) {
        String pin = body != null ? body.get("pin") : null;
        if (pin == null || !staffPin.equals(pin)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect staff PIN");
        }
        String token = jwtService.generateToken("staff", "STAFF");
        setAuthCookie(response, token);
        return Map.of(
                "ok", true,
                "role", "STAFF",
                "token", token,
                "user", Map.of("username", "staff", "role", "STAFF")
        );
    }

    @GetMapping("/auth/me")
    public Map<String, Object> authMe(HttpServletRequest request) {
        String role = (String) request.getAttribute(AuthFilter.ATTR_ROLE);
        String username = (String) request.getAttribute(AuthFilter.ATTR_USER);
        if (role == null || username == null) {
            String token = resolveToken(request);
            JwtService.TokenClaims claims = jwtService.validateAndParseToken(token);
            if (claims == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session");
            }
            role = claims.role();
            username = claims.username();
        }
        return Map.of(
                "ok", true,
                "authenticated", true,
                "role", role,
                "username", username
        );
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> authLogout(HttpServletResponse response) {
        clearAuthCookie(response);
        return Map.of("ok", true, "message", "Logged out successfully");
    }

    @PostMapping("/menu")
    public MenuItem add(@RequestBody MenuItem item) {
        item.id = null;
        if (item.name == null || item.name.isBlank() || item.category == null || item.category.isBlank() || item.price < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, category and valid price are required");
        }
        if (item.emoji == null || item.emoji.isBlank()) {
            item.emoji = "🍽️";
        }
        return menu.save(item);
    }

    @PutMapping("/menu/{id}")
    public MenuItem update(@PathVariable Long id, @RequestBody MenuItem changes) {
        MenuItem item = menu.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dish not found"));
        if (changes.price < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price cannot be negative");
        }
        item.price = changes.price;
        item.published = changes.published;
        return menu.save(item);
    }

    @DeleteMapping("/menu/{id}")
    public Map<String, Boolean> remove(@PathVariable Long id) {
        if (!menu.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dish not found");
        }
        menu.deleteById(id);
        return Map.of("deleted", true);
    }

    @GetMapping("/orders")
    public List<RestaurantOrder> allOrders() {
        return orders.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/orders/{id}")
    public RestaurantOrder order(@PathVariable Long id) {
        return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @PostMapping("/orders")
    public RestaurantOrder place(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("customerName", "")).trim();
        String type = String.valueOf(body.getOrDefault("orderType", "Dine-In"));
        String table = String.valueOf(body.getOrDefault("tableNumber", ""));
        Object raw = body.get("items");
        if (name.isBlank() || raw == null || !(raw instanceof List<?>) || ((List<?>) raw).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name and at least one dish are required");
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) raw;
        double subtotal = 0;
        for (Map<String, Object> row : items) {
            long id = Long.parseLong(String.valueOf(row.get("id")));
            int qty = Integer.parseInt(String.valueOf(row.get("quantity")));
            MenuItem mi = menu.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dish not found"));
            if (!mi.published) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mi.name + " is unavailable");
            }
            if (qty < 1 || qty > 99) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid quantity");
            }
            subtotal += mi.price * qty;
            row.put("name", mi.name);
            row.put("price", mi.price);
            row.put("emoji", mi.emoji);
        }
        String coupon = String.valueOf(body.getOrDefault("coupon", "")).trim().toUpperCase();
        double discount = "SAVE10".equals(coupon) ? subtotal * .10 : 0, tax = (subtotal - discount) * .05;
        RestaurantOrder o = new RestaurantOrder();
        o.customerName = name;
        o.orderType = type;
        o.tableNumber = table;
        o.subtotal = round(subtotal);
        o.discount = round(discount);
        o.tax = round(tax);
        o.total = round(subtotal - discount + tax);
        o.coupon = coupon;
        o.itemsJson = toJson(items);
        return orders.save(o);
    }

    @PutMapping("/orders/{id}/status")
    public RestaurantOrder status(@PathVariable Long id, @RequestBody Map<String, String> body) {
        RestaurantOrder o = order(id);
        String next = body.get("status");
        if (!List.of("PLACED", "RECEIVED", "IN-PROGRESS", "COMPLETED").contains(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
        List<String> flow = List.of("PLACED", "RECEIVED", "IN-PROGRESS", "COMPLETED");
        if (flow.indexOf(next) < flow.indexOf(o.status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order status cannot move backwards");
        }
        o.status = next;
        return orders.save(o);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<MenuItem> all = menu.findAll();
        List<RestaurantOrder> os = orders.findAll();
        return Map.of(
                "dishes", all.size(),
                "published", all.stream().filter(x -> x.published).count(),
                "orders", os.size(),
                "revenue", round(os.stream().filter(x -> "COMPLETED".equals(x.status)).mapToDouble(x -> x.total).sum())
        );
    }

    private void setAuthCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("DINEFLOW_AUTH", token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(1))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("DINEFLOW_AUTH", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7).trim();
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("DINEFLOW_AUTH".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private double round(double n) {
        return Math.round(n * 100.0) / 100.0;
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }
}
