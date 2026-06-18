package com.example.fileserver.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class ApiKeyVerificationController {

    // 인증 필터를 통과한 요청에 API 키 유효성을 확인하는 경량 응답을 반환한다.
    @GetMapping("/verify")
    public VerificationResponse verify() {
        return new VerificationResponse(true);
    }

    public record VerificationResponse(boolean authenticated) {
    }
}
