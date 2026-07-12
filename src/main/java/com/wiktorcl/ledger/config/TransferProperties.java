package com.wiktorcl.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger.transfer")
public record TransferProperties(int maxRetries) {
}
