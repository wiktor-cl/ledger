package com.wiktorcl.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
