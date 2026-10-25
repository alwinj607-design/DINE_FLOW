package com.dineflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class AuthFilter extends OncePerRequestFilter {
    public static final String ATTR_USER = "authenticatedUser";
    public static final String ATTR_ROLE = "authenticatedRole";

    private final JwtService jwtService;

    public AuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        // Allow CORS pre-flight requests
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Resolve token from Authorization header or cookie
        String token = resolveToken(request);
        JwtService.TokenClaims claims = null;
        if (token != null && !token.isBlank()) {
            claims = jwtService.validateAndParseToken(token);
            if (claims != null) {
                request.setAttribute(ATTR_USER, claims.username());
                request.setAttribute(ATTR_ROLE, claims.role());
            }
        }

        // Enforce role-based access control on protected API endpoints
        if (isAdminPath(path, method)) {
            if (claims == null) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required. Please log in.");
                return;
            }
            if (!"ADMIN".equalsIgnoreCase(claims.role())) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN, "Admin privileges required.");
                return;
            }
        }

        if (isStaffPath(path, method)) {
            if (claims == null) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required. Please log in.");
                return;
            }
            if (!"STAFF".equalsIgnoreCase(claims.role())) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN, "Staff privileges required.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAdminPath(String path, String method) {
        if ("/api/stats".equals(path) && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        if ("/api/menu".equals(path) && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        if (path.startsWith("/api/menu/") && ("PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return true;
        }
        return false;
    }

    private boolean isStaffPath(String path, String method) {
        if (("/api/orders".equals(path) || "/api/orders/".equals(path)) && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        if (path.startsWith("/api/orders/") && path.endsWith("/status") && "PUT".equalsIgnoreCase(method)) {
            return true;
        }
        return false;
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

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String json = "{\"ok\":false,\"error\":\"" + (status == 401 ? "Unauthorized" : "Forbidden") + "\",\"message\":\"" + message + "\"}";
        response.getWriter().write(json);
    }
}

