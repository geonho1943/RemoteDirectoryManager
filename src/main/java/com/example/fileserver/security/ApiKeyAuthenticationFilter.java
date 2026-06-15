package com.example.fileserver.security;

import com.example.fileserver.common.response.ErrorResponse;
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
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1";
    private static final String HEALTH_PATH = "/api/v1/health";
    private static final String API_KEY_HEADER_NAME = "API-Key";

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    // API 키 검증에 필요한 보안 설정과 JSON 직렬화 도구를 주입한다.
    public ApiKeyAuthenticationFilter(
            SecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    // 보호 대상이 아닌 경로나 헬스 체크 요청은 필터 검증에서 제외한다.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = pathWithinApplication(request);
        if (!isProtectedApiPath(requestPath)) {
            return true;
        }

        return HEALTH_PATH.equals(requestPath);
    }

    // API-Key 헤더를 SHA-256 해시로 비교하고 실패 시 JSON 401 응답을 작성한다.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestPath = pathWithinApplication(request);

        String rawApiKey = request.getHeader(API_KEY_HEADER_NAME);
        if (rawApiKey == null || rawApiKey.trim().isEmpty()) {
            writeUnauthorizedResponse(response, requestPath, "Missing API key.");
            return;
        }

        String requestHash = sha256Hex(rawApiKey.trim());
        String configuredHash = securityProperties.adminKeyHash();

        if (!MessageDigest.isEqual(
                requestHash.getBytes(StandardCharsets.UTF_8),
                configuredHash.getBytes(StandardCharsets.UTF_8)
        )) {
            writeUnauthorizedResponse(response, requestPath, "Invalid API key.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    // 요청 경로가 API 보호 영역에 속하는지 판별한다.
    private boolean isProtectedApiPath(String requestPath) {
        return API_PREFIX.equals(requestPath) || requestPath.startsWith(API_PREFIX + "/");
    }

    // 컨텍스트 경로를 제외한 애플리케이션 내부 요청 경로를 구한다.
    private String pathWithinApplication(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();

        if (contextPath == null || contextPath.isEmpty()) {
            return requestUri;
        }

        return requestUri.substring(contextPath.length());
    }

    // 인증 실패 응답을 공통 에러 JSON 형식으로 작성한다.
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

    // 입력 문자열을 SHA-256 해시의 16진수 표현으로 변환한다.
    private static String sha256Hex(String rawValue) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hash = messageDigest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }
}
