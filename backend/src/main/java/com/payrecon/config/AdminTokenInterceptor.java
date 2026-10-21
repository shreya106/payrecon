package com.payrecon.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Read requests are public. Anything that changes data needs the admin token.
 */
@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final byte[] expectedToken;

    public AdminTokenInterceptor(@Value("${payrecon.admin-token}") String token) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (READ_ONLY_METHODS.contains(request.getMethod())) {
            return true;
        }

        String given = request.getHeader("X-Admin-Token");
        boolean valid = expectedToken.length > 0
                && given != null
                && MessageDigest.isEqual(expectedToken, given.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Admin token required");
            return false;
        }
        return true;
    }
}