package com.example.fileserver.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String adminKeyHash) {

    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public SecurityProperties {
        if (adminKeyHash == null || adminKeyHash.isBlank()) {
            throw new IllegalArgumentException("app.security.admin-key-hash must not be blank.");
        }

        adminKeyHash = adminKeyHash.trim().toLowerCase(Locale.ROOT);
        if (!SHA_256_HEX.matcher(adminKeyHash).matches()) {
            throw new IllegalArgumentException("app.security.admin-key-hash must be a 64-character lowercase SHA-256 hex string.");
        }
    }
}
