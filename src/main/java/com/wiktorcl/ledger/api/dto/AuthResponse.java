package com.wiktorcl.ledger.api.dto;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt) {
}
