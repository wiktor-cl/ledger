package com.wiktorcl.ledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wiktorcl.ledger.config.JwtProperties;
import com.wiktorcl.ledger.security.JwtService;
import com.wiktorcl.ledger.security.Role;
import com.wiktorcl.ledger.security.SecurityConfig;
import com.wiktorcl.ledger.service.TransferCommand;
import com.wiktorcl.ledger.service.TransferResult;
import com.wiktorcl.ledger.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A no-database slice test isolating whether a missing Idempotency-Key
 * header is rejected with 400 as designed, without needing Testcontainers -
 * written to diagnose a CI-only failure (the full integration test reported
 * 401 instead of 400) where a Docker-less local run couldn't reproduce it.
 * Uses a real JwtService/JwtAuthFilter (not mocked) so the request goes
 * through the exact same authentication path production traffic does.
 */
@WebMvcTest(TransferController.class)
@Import({SecurityConfig.class, JwtService.class})
class TransferControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private TransferService transferService;

    private String realBearerToken() {
        return "Bearer " + jwtService.generate("test-user", Role.USER).token();
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class JwtPropertiesTestConfig {
        @org.springframework.context.annotation.Bean
        JwtProperties jwtProperties() {
            return new JwtProperties("test-only-secret-at-least-32-bytes-long-000000", 60);
        }
    }

    @Test
    void missingIdempotencyKeyHeaderReturns400WithRealToken() throws Exception {
        when(transferService.transfer(any(TransferCommand.class))).thenReturn(
                new TransferResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, false));

        String body = objectMapper.writeValueAsString(new com.wiktorcl.ledger.api.dto.TransferRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.00"), "no key"));

        mockMvc.perform(post("/transfers")
                        .header("Authorization", realBearerToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void missingIdempotencyKeyHeaderReturns400() throws Exception {
        when(transferService.transfer(any(TransferCommand.class))).thenReturn(
                new TransferResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, false));

        String body = objectMapper.writeValueAsString(new com.wiktorcl.ledger.api.dto.TransferRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.00"), "no key"));

        mockMvc.perform(post("/transfers")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noAuthenticationReturns401() throws Exception {
        String body = objectMapper.writeValueAsString(new com.wiktorcl.ledger.api.dto.TransferRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.00"), "no key"));

        mockMvc.perform(post("/transfers")
                        .contentType("application/json")
                        .header("Idempotency-Key", "test-key")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
