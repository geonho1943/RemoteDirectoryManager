package com.example.fileserver.security;

import com.example.fileserver.common.response.ErrorResponse;
import com.example.fileserver.common.error.UnauthorizedApiKeyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1";
    private static final String HEALTH_PATH = "/api/v1/health";
    private static final String API_KEY_HEADER_NAME = "API-Key";

    private final SecurityProperties securityProperties;
    private final ApiKeyHasher apiKeyHasher;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(
            SecurityProperties securityProperties,
            ApiKeyHasher apiKeyHasher,
            ObjectMapper objectMapper
    ) {
        this.securityProperties = securityProperties;
        this.apiKeyHasher = apiKeyHasher;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = pathWithinApplication(request);
        if (!isProtectedApiPath(requestPath)) {
            return true;
        }

        return HEALTH_PATH.equals(requestPath);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestPath = pathWithinApplication(request);

        try {
            String rawApiKey = request.getHeader(API_KEY_HEADER_NAME);
            if (rawApiKey == null || rawApiKey.trim().isEmpty()) {
                throw new UnauthorizedApiKeyException("Missing API key.");
            }

            String requestHash = apiKeyHasher.sha256Hex(rawApiKey.trim());
            String configuredHash = securityProperties.adminKeyHash();

            if (!MessageDigest.isEqual(
                    requestHash.getBytes(StandardCharsets.UTF_8),
                    configuredHash.getBytes(StandardCharsets.UTF_8)
            )) {
                throw new UnauthorizedApiKeyException("Invalid API key.");
            }

            filterChain.doFilter(request, response);
        } catch (UnauthorizedApiKeyException exception) {
            writeUnauthorizedResponse(response, requestPath, exception.getMessage());
        }
    }

    private boolean isProtectedApiPath(String requestPath) {
        return API_PREFIX.equals(requestPath) || requestPath.startsWith(API_PREFIX + "/");
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();

        if (contextPath == null || contextPath.isEmpty()) {
            return requestUri;
        }

        return requestUri.substring(contextPath.length());
    }

    private void writeUnauthorizedResponse(
            HttpServletResponse response,
            String requestPath,
            String message
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = new ErrorResponse(
                "UNAUTHORIZED_API_KEY",
                message,
                requestPath,
                LocalDateTime.now()
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}
