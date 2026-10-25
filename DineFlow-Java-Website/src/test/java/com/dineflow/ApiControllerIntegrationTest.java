package com.dineflow;

import com.dineflow.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
public class ApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testPublicEndpointsAccessibleWithoutAuth() throws Exception {
        // Customer can read published menu
        mockMvc.perform(get("/api/menu/published"))
                .andExpect(status().isOk());

        // Customer can read full menu
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk());
    }

    @Test
    void testAdminLoginSuccessAndFailure() throws Exception {
        // Incorrect password
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        // Correct password
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(cookie().exists("DINEFLOW_AUTH"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(json, Map.class);
        String token = (String) map.get("token");
        assertNotNull(token);

        JwtService.TokenClaims claims = jwtService.validateAndParseToken(token);
        assertNotNull(claims);
        assertEquals("ADMIN", claims.role());
    }

    @Test
    void testStaffLoginSuccessAndFailure() throws Exception {
        // Incorrect PIN
        mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized());

        // Correct PIN
        MvcResult result = mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(cookie().exists("DINEFLOW_AUTH"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(json, Map.class);
        String token = (String) map.get("token");
        assertNotNull(token);

        JwtService.TokenClaims claims = jwtService.validateAndParseToken(token);
        assertNotNull(claims);
        assertEquals("STAFF", claims.role());
    }

    @Test
    void testAuthMeEndpoint() throws Exception {
        // Unauthenticated request to /auth/me returns 401
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        // Invalid token returns 401
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());

        // Admin token returns role ADMIN
        String adminToken = jwtService.generateToken("admin", "ADMIN");
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value("admin"));

        // Staff token via Cookie returns role STAFF
        String staffToken = jwtService.generateToken("staff", "STAFF");
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("DINEFLOW_AUTH", staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.username").value("staff"));
    }

    @Test
    void testAdminProtectedEndpoints() throws Exception {
        // Unauthenticated request to /api/stats -> 401
        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isUnauthorized());

        // Staff token to /api/stats -> 403 Forbidden
        String staffToken = jwtService.generateToken("staff", "STAFF");
        mockMvc.perform(get("/api/stats")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        // Admin token to /api/stats -> 200 OK
        String adminToken = jwtService.generateToken("admin", "ADMIN");
        mockMvc.perform(get("/api/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dishes").isNumber());

        // Staff cannot add dish -> 403 Forbidden
        mockMvc.perform(post("/api/menu")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"category\":\"Snacks\",\"price\":50}"))
                .andExpect(status().isForbidden());

        // Admin can add dish -> 200 OK
        mockMvc.perform(post("/api/menu")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Dish\",\"category\":\"Snacks\",\"price\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Dish"));
    }

    @Test
    void testStaffProtectedEndpoints() throws Exception {
        // Unauthenticated request to /api/orders -> 401
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());

        // Admin cannot access staff-only incoming orders board -> 403 Forbidden
        String adminToken = jwtService.generateToken("admin", "ADMIN");
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // Staff token to /api/orders -> 200 OK
        String staffToken = jwtService.generateToken("staff", "STAFF");
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());
    }

    @Test
    void testCustomerOrderPlacementAndReceiptAccess() throws Exception {
        // Customer places an order without auth
        MvcResult orderResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Alice\",\"orderType\":\"Dine-In\",\"tableNumber\":\"T1\",\"items\":[{\"id\":1,\"quantity\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Alice"))
                .andExpect(jsonPath("$.total").isNumber())
                .andReturn();

        String json = orderResult.getResponse().getContentAsString();
        Map<?, ?> orderMap = objectMapper.readValue(json, Map.class);
        Number orderId = (Number) orderMap.get("id");

        // Customer views order receipt without auth
        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()));

        // Admin trying to change status -> 403 Forbidden
        String adminToken = jwtService.generateToken("admin", "ADMIN");
        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isForbidden());

        // Staff changing status -> 200 OK
        String staffToken = jwtService.generateToken("staff", "STAFF");
        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void testLogoutClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("DINEFLOW_AUTH", 0));
    }
}

