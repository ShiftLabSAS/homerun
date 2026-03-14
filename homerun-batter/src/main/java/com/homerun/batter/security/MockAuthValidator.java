package com.homerun.batter.security;

import com.homerun.batter.autoconfigure.BatterProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Validates that an incoming request is allowed to activate mock mode.
 *
 * <p>Two checks are applied in order:
 * <ol>
 *   <li><b>Token check</b> — the {@code X-Mock-Auth-Token} header value must
 *       match the configured shared secret (constant-time comparison to
 *       prevent timing attacks).</li>
 *   <li><b>IP allowlist check</b> — the remote address must fall within one of
 *       the configured CIDR ranges. Ranges are matched by simple prefix comparison;
 *       replace with a proper CIDR library (e.g. Apache Commons Net
 *       {@code SubnetUtils}) for production-grade checks.</li>
 * </ol>
 *
 * <p>Both checks must pass. Either alone is insufficient.
 */
@Slf4j
@RequiredArgsConstructor
public class MockAuthValidator {

    private final BatterProperties properties;

    public boolean isAuthorized(String token, HttpServletRequest request) {
        if (!isTokenValid(token)) {
            log.warn("Mock auth rejected: invalid token from {}", request.getRemoteAddr());
            return false;
        }
        if (!isIpAllowed(request.getRemoteAddr())) {
            log.warn("Mock auth rejected: IP {} not in allowlist", request.getRemoteAddr());
            return false;
        }
        return true;
    }

    private boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) return false;
        String expected = properties.getAuthToken();
        if (expected == null || expected.isBlank()) return false;

        // Constant-time comparison prevents timing-based token enumeration.
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isIpAllowed(String remoteAddr) {
        List<String> allowedRanges = properties.getAllowedIpRanges();
        if (allowedRanges == null || allowedRanges.isEmpty()) {
            // No allowlist configured → allow all IPs (suitable for local dev).
            return true;
        }
        return allowedRanges.stream().anyMatch(remoteAddr::startsWith);
    }
}
